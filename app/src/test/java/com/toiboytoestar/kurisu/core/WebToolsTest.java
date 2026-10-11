package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebToolsTest {

    @Test
    public void htmlToTextDropsScriptsAndDecodesEntities() {
        String html = "<html><head><title>My &amp; Page</title><style>p{}</style></head><body>"
                + "<script>alert(1)</script><h1>Neurons</h1><p>Cells &lt;fire&gt; &#8211; fast.</p>"
                + "<ul><li>One</li><li>Two</li></ul><!-- c --></body></html>";
        assertEquals("My & Page", Html.title(html));
        String t = Html.toText(html, 1000);
        assertTrue(t, t.contains("Neurons"));
        assertTrue(t, t.contains("Cells <fire>"));
        assertTrue(t, t.contains("One") && t.contains("Two") && t.indexOf("One") < t.indexOf("Two"));
        assertFalse(t, t.contains("alert"));
        assertFalse(t, t.contains("p{}"));
    }

    @Test
    public void parsesArxivAtom() {
        String xml = "<feed><entry><id>http://arxiv.org/abs/1234.5678v1</id><published>2021-05-04T10:00:00Z</published>"
                + "<title>Attention   Is\n Useful</title><summary>We show that &amp; prove things.</summary>"
                + "<author><name>Ada Lovelace</name></author><author><name>Alan Turing</name></author></entry>"
                + "<entry><id>http://arxiv.org/abs/9999.0001v2</id><published>2019-01-01T00:00:00Z</published>"
                + "<title>Second</title><summary>Short.</summary><author><name>X Y</name></author></entry></feed>";
        String out = WebTools.parseArxiv(xml);
        assertTrue(out, out.startsWith("1. Attention Is Useful (2021)"));
        assertTrue(out, out.contains("Ada Lovelace, Alan Turing"));
        assertTrue(out, out.contains("We show that & prove things."));
        assertTrue(out, out.contains("2. Second (2019)"));
        assertEquals("No arXiv papers found.", WebTools.parseArxiv("<feed></feed>"));
    }

    @Test
    public void parsesWikipediaExtracts() throws Exception {
        String json = "{\"query\":{\"pages\":{\"2\":{\"title\":\"Hippocampus\",\"index\":2,\"extract\":\"The hippocampus is a brain structure.\"},"
                + "\"1\":{\"title\":\"Memory\",\"index\":1,\"extract\":\"Memory is the faculty of encoding.\"}}}}";
        String out = WebTools.parseWikipedia("https://en.wikipedia.org", json);
        assertTrue(out, out.startsWith("## Memory"));
        assertTrue(out, out.contains("https://en.wikipedia.org/wiki/Hippocampus"));
        assertEquals("No Wikipedia article found.", WebTools.parseWikipedia("https://x", "{\"batchcomplete\":\"\"}"));
    }

    @Test
    public void refusesPrivateHosts() {
        assertTrue(WebTools.isPrivateHost("localhost"));
        assertTrue(WebTools.isPrivateHost("127.0.0.1"));
        assertTrue(WebTools.isPrivateHost("192.168.1.1"));
        assertTrue(WebTools.isPrivateHost("10.0.0.5"));
        assertTrue(WebTools.isPrivateHost("169.254.169.254"));
        assertTrue(WebTools.isPrivateHost("router"));
        assertFalse(WebTools.isPrivateHost("93.184.216.34"));
    }
}
