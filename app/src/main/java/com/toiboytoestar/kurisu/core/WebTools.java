package com.toiboytoestar.kurisu.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Research helpers that need no API keys: Wikipedia, arXiv and plain page fetching. */
public final class WebTools {
    private WebTools() {}

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String getText(String url, int maxBytes) throws IOException {
        HttpURLConnection c = Http.open(url, "GET");
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code);
            return Http.readAll(c.getInputStream(), maxBytes);
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- Wikipedia

    public static String wikipedia(String query, String lang) throws IOException {
        return wikipediaAt("https://" + (lang == null || lang.length() == 0 ? "en" : lang) + ".wikipedia.org", query);
    }

    static String wikipediaAt(String origin, String query) throws IOException {
        String url = origin + "/w/api.php?action=query&format=json&redirects=1&generator=search&gsrlimit=3"
                + "&prop=extracts&exintro=1&explaintext=1&exchars=1100&gsrsearch=" + enc(query);
        String body = getText(url, 200000);
        return parseWikipedia(origin, body);
    }

    static String parseWikipedia(String origin, String body) throws IOException {
        try {
            JSONObject q = new JSONObject(body).optJSONObject("query");
            if (q == null || q.optJSONObject("pages") == null) return "No Wikipedia article found.";
            JSONObject pages = q.getJSONObject("pages");
            List<JSONObject> list = new ArrayList<JSONObject>();
            Iterator<?> it = pages.keys();
            while (it.hasNext()) list.add(pages.getJSONObject((String) it.next()));
            java.util.Collections.sort(list, new java.util.Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optInt("index", 99) - b.optInt("index", 99);
                }
            });
            StringBuilder sb = new StringBuilder();
            for (JSONObject p : list) {
                String title = p.optString("title");
                String extract = p.optString("extract").trim();
                if (extract.length() == 0) continue;
                sb.append("## ").append(title).append("\n").append(extract).append("\nSource: ")
                        .append(origin).append("/wiki/").append(title.replace(' ', '_')).append("\n\n");
            }
            return sb.length() == 0 ? "No Wikipedia article found." : sb.toString().trim();
        } catch (JSONException e) {
            throw new IOException("Unreadable Wikipedia response");
        }
    }

    // -------------------------------------------------------------------- arXiv

    public static String arxiv(String query) throws IOException {
        return arxivAt("https://export.arxiv.org", query);
    }

    static String arxivAt(String origin, String query) throws IOException {
        String url = origin + "/api/query?max_results=5&sortBy=relevance&search_query=all:" + enc(query.trim().replaceAll("\\s+", "+")).replace("%2B", "+");
        return parseArxiv(getText(url, 400000));
    }

    static String parseArxiv(String xml) {
        Pattern entry = Pattern.compile("(?s)<entry>(.*?)</entry>");
        Matcher m = entry.matcher(xml);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find() && n < 5) {
            String e = m.group(1);
            String title = tag(e, "title");
            String summary = tag(e, "summary");
            String published = tag(e, "published");
            String id = tag(e, "id");
            List<String> authors = new ArrayList<String>();
            Matcher a = Pattern.compile("(?s)<author>\\s*<name>(.*?)</name>").matcher(e);
            while (a.find() && authors.size() < 4) authors.add(Html.decode(a.group(1).trim()));
            if (title.length() == 0) continue;
            n++;
            sb.append(n).append(". ").append(title);
            if (published.length() >= 4) sb.append(" (").append(published.substring(0, 4)).append(")");
            sb.append("\n   Authors: ").append(authors.isEmpty() ? "unknown" : join(authors, ", ")).append("\n");
            if (summary.length() > 450) summary = summary.substring(0, 450) + "...";
            sb.append("   Abstract: ").append(summary).append("\n   Link: ").append(id).append("\n");
        }
        return n == 0 ? "No arXiv papers found." : sb.toString().trim();
    }

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("(?s)<" + name + "[^>]*>(.*?)</" + name + ">").matcher(xml);
        return m.find() ? Html.decode(m.group(1)).replaceAll("\\s+", " ").trim() : "";
    }

    private static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? sep : "").append(l.get(i));
        return sb.toString();
    }

    // -------------------------------------------------------------------- pages

    /** Returns true if the host is loopback / private / link-local, which we refuse to fetch. */
    static boolean isPrivateHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.indexOf('.') < 0 && !h.contains(":")) return true;
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
                byte[] b = a.getAddress();
                if (b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 0x40) return true; // 100.64/10 CGNAT
                if (b.length == 16 && (b[0] & 0xfe) == 0xfc) return true; // fc00::/7
            }
        } catch (IOException e) {
            return false; // unresolvable: the fetch itself will fail
        }
        return false;
    }

    public static String fetchPage(String url, int maxChars) throws IOException {
        URL u = new URL(url.trim());
        HttpURLConnection c = null;
        try {
            for (int hop = 0; ; hop++) {
                String proto = u.getProtocol();
                if (!"http".equals(proto) && !"https".equals(proto)) throw new IOException("Only http(s) links can be read");
                if (isPrivateHost(u.getHost())) throw new IOException("Refusing to read a local or private address");
                c = Http.open(u.toString(), "GET");
                c.setReadTimeout(20000);
                c.setRequestProperty("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5");
                c.setInstanceFollowRedirects(false);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null || hop >= 4) throw new IOException("Too many redirects");
                    u = new URL(u, loc);
                    continue;
                }
                if (code / 100 != 2) throw new IOException("HTTP " + code);
                break;
            }
            String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
            if (type.length() > 0 && !(type.contains("text") || type.contains("html") || type.contains("json") || type.contains("xml"))) {
                throw new IOException("Unsupported content type (" + type + ")");
            }
            String body = Http.readAll(c.getInputStream(), 600000);
            if (type.contains("html") || body.trim().startsWith("<")) {
                String title = Html.title(body);
                String text = Html.toText(body, maxChars);
                return (title.length() > 0 ? "Title: " + title + "\n" : "") + text;
            }
            return body.length() > maxChars ? body.substring(0, maxChars) + "\n[truncated]" : body;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
