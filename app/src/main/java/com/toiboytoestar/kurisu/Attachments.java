package com.toiboytoestar.kurisu;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Turns a picked photo or PDF into small base64 JPEGs a vision model can read. Run off the main thread. */
final class Attachments {
    private Attachments() {}

    static final int MAX_PDF_PAGES = 6;
    private static final int MAX_SIDE_IMAGE = 1280;
    private static final int MAX_SIDE_PDF = 1100;

    static final class Result {
        final List<String> images = new ArrayList<String>();
        String label = "";
        int totalPages;
        String error;
    }

    static Result load(Context ctx, Uri uri) {
        Result r = new Result();
        try {
            ContentResolver cr = ctx.getContentResolver();
            String type = cr.getType(uri);
            boolean pdf = (type != null && type.contains("pdf")) || String.valueOf(uri).toLowerCase().endsWith(".pdf");
            if (pdf) {
                if (Build.VERSION.SDK_INT < 21) {
                    r.error = "PDF reading needs Android 5 or newer.";
                    return r;
                }
                loadPdf(cr, uri, r);
            } else {
                loadImage(cr, uri, r);
            }
        } catch (OutOfMemoryError e) {
            r.images.clear();
            r.error = "That file is too large for this phone to open.";
        } catch (Exception e) {
            r.images.clear();
            r.error = "Couldn't open that file" + (e.getMessage() != null ? ": " + e.getMessage() : ".");
        }
        if (r.error == null && r.images.isEmpty()) r.error = "Nothing readable in that file.";
        return r;
    }

    private static void loadImage(ContentResolver cr, Uri uri, Result r) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("unreadable");
        try {
            BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
        int longest = Math.max(o.outWidth, o.outHeight);
        int sample = 1;
        while (longest / (sample * 2) >= MAX_SIDE_IMAGE) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        in = cr.openInputStream(uri);
        if (in == null) throw new IOException("unreadable");
        Bitmap b;
        try {
            b = BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
        if (b == null) throw new IOException("not an image");
        b = rotateUpright(cr, uri, b);
        r.images.add(toBase64(flatten(b, MAX_SIDE_IMAGE)));
        r.label = "photo";
        r.totalPages = 1;
    }

    /** Camera photos are often stored sideways with an EXIF flag; vision models read them badly unless we turn them upright. */
    private static Bitmap rotateUpright(ContentResolver cr, Uri uri, Bitmap b) {
        int degrees = 0;
        ParcelFileDescriptor fd = null;
        try {
            fd = cr.openFileDescriptor(uri, "r");
            if (fd != null) {
                // ExifInterface(InputStream) needs API 24; the fd path works on every supported version
                android.media.ExifInterface ex = new android.media.ExifInterface("/proc/self/fd/" + fd.getFd());
                switch (ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    case android.media.ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                    case android.media.ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                    default: break;
                }
            }
        } catch (Exception ignored) {
            // no EXIF (e.g. a screenshot) or unreadable: keep as is
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException ignored) { }
            }
        }
        if (degrees == 0) return b;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(degrees);
        try {
            Bitmap rot = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (rot != b) b.recycle();
            return rot;
        } catch (OutOfMemoryError e) {
            return b;
        }
    }

    private static void loadPdf(ContentResolver cr, Uri uri, Result r) throws IOException {
        ParcelFileDescriptor fd = cr.openFileDescriptor(uri, "r");
        if (fd == null) throw new IOException("unreadable");
        PdfRenderer pr = null;
        try {
            pr = new PdfRenderer(fd);
            r.totalPages = pr.getPageCount();
            int n = Math.min(r.totalPages, MAX_PDF_PAGES);
            for (int i = 0; i < n; i++) {
                PdfRenderer.Page page = pr.openPage(i);
                try {
                    float scale = MAX_SIDE_PDF / (float) Math.max(page.getWidth(), page.getHeight());
                    Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(page.getWidth() * scale)),
                            Math.max(1, Math.round(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE); // pages render transparent otherwise
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    r.images.add(toBase64(bmp));
                    bmp.recycle();
                } finally {
                    page.close();
                }
            }
            r.label = "PDF";
        } catch (SecurityException e) {
            r.error = "That PDF is password protected.";
        } finally {
            if (pr != null) pr.close();
            fd.close();
        }
    }

    /** Scales down to max side and drops transparency (JPEG has none). */
    private static Bitmap flatten(Bitmap src, int maxSide) {
        float s = Math.min(1f, maxSide / (float) Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, Math.round(src.getWidth() * s));
        int h = Math.max(1, Math.round(src.getHeight() * s));
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.WHITE);
        Canvas c = new Canvas(out);
        c.drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0, 0, null);
        if (src != out) src.recycle();
        return out;
    }

    private static String toBase64(Bitmap b) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 78, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }
}
