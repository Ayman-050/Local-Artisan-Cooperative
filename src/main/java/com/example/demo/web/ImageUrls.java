package com.example.demo.web;

import java.util.Locale;

/**
 * Validation for user-supplied image references (profile pictures, banners, product photos).
 * Accepts http(s) URLs, site-relative paths and {@code data:image/...} URLs up to a size limit.
 * Rejects anything else (e.g. {@code javascript:} URLs) so stored values are safe to put in {@code src}.
 *
 * <p>Images are still stored inline in the database as data URLs; moving them to file/object storage
 * is the proper fix (see README "Known limitations").
 */
public final class ImageUrls {

    /** ~3 MB of base64 text, roughly a 2.2 MB image. */
    public static final int MAX_DATA_URL_LENGTH = 3_000_000;
    private static final int MAX_URL_LENGTH = 2_000;

    private ImageUrls() {
    }

    /** @return the trimmed value, or null for blank input. @throws IllegalArgumentException when not allowed. */
    public static String validate(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:")) {
            if (!lower.startsWith("data:image/") || lower.startsWith("data:image/svg")) {
                throw new IllegalArgumentException(fieldLabel + " must be a PNG, JPEG, GIF or WebP image.");
            }
            if (v.length() > MAX_DATA_URL_LENGTH) {
                throw new IllegalArgumentException(fieldLabel + " is too large (max about 2 MB).");
            }
            return v;
        }
        if (v.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(fieldLabel + " URL is too long.");
        }
        if (lower.startsWith("https://") || lower.startsWith("http://")
                || (v.startsWith("/") && !v.startsWith("//"))) {
            return v;
        }
        throw new IllegalArgumentException(fieldLabel + " must be an http(s) URL or an uploaded image.");
    }

    /** Validates a list of images serialised as one string (refund evidence is stored as JSON/CSV text). */
    public static String validateBlob(String value, String fieldLabel, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " are too large.");
        }
        if (value.toLowerCase(Locale.ROOT).contains("javascript:")) {
            throw new IllegalArgumentException(fieldLabel + " contain an invalid URL.");
        }
        return value;
    }
}
