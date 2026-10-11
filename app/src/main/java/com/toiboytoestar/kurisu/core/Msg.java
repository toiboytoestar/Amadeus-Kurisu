package com.toiboytoestar.kurisu.core;

import java.util.Collections;
import java.util.List;

/** One chat message. role is "user" or "assistant". May carry JPEG images (base64) for vision-capable models. */
public final class Msg {
    public final String role;
    public final String text;
    /** Base64 (no wrapper) JPEG images attached to this message; never null. */
    public final List<String> images;

    public Msg(String role, String text) {
        this(role, text, Collections.<String>emptyList());
    }

    public Msg(String role, String text, List<String> images) {
        this.role = role;
        this.text = text;
        this.images = images == null ? Collections.<String>emptyList() : images;
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    /** Same message without its images (older images are dropped from context to save tokens). */
    public Msg withoutImages() {
        if (images.isEmpty()) return this;
        String t = text == null ? "" : text;
        return new Msg(role, t + (t.length() > 0 ? " " : "") + "[an image was attached earlier]");
    }
}
