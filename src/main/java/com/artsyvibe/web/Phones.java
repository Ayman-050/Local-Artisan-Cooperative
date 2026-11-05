package com.artsyvibe.web;

/**
 * Phone numbers are typed in many formats ("+973 3311-2201", "(973) 33112201"). They are accepted with
 * spaces, dashes, dots and brackets and stored normalised: an optional leading "+" followed by digits.
 */
public final class Phones {

    private Phones() {
    }

    /** @return the normalised number. @throws IllegalArgumentException if it is not a plausible phone number. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Please enter a valid phone number.");
        }
        String trimmed = raw.trim();
        boolean plus = trimmed.startsWith("+") || trimmed.startsWith("00");
        String digits = trimmed.replaceAll("[\\s\\-.()]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (!digits.matches("\\d{7,15}")) {
            throw new IllegalArgumentException(
                    "Please enter a valid phone number (digits, optionally with spaces or dashes, e.g. +973 3311 2201).");
        }
        return (plus ? "+" : "") + digits;
    }
}
