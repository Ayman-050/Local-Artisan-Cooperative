package com.artsyvibe.controller;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parsing helpers for the loosely-typed {@code Map<String, Object>} JSON bodies the front end sends
 * (numbers may arrive as numbers or strings). Bad input becomes IllegalArgumentException, i.e. HTTP 400.
 */
final class RequestValues {

    private RequestValues() {
    }

    static String optionalString(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v != null ? String.valueOf(v) : null;
    }

    static String requiredString(Map<String, Object> body, String key) {
        String v = optionalString(body, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v.trim();
    }

    static Long optionalLong(Map<String, Object> body, String key) {
        String v = optionalString(body, key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(key + " must be a whole number");
        }
    }

    static Long requiredLong(Map<String, Object> body, String key) {
        Long v = optionalLong(body, key);
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    static int optionalInt(Map<String, Object> body, String key, int defaultValue) {
        Long v = optionalLong(body, key);
        if (v == null) {
            return defaultValue;
        }
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(key + " is out of range");
        }
        return v.intValue();
    }

    static int requiredInt(Map<String, Object> body, String key) {
        Long v = requiredLong(body, key);
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(key + " is out of range");
        }
        return v.intValue();
    }

    static BigDecimal requiredMoney(Map<String, Object> body, String key) {
        String v = requiredString(body, key);
        try {
            BigDecimal amount = new BigDecimal(v);
            if (amount.signum() < 0) {
                throw new IllegalArgumentException(key + " cannot be negative");
            }
            if (amount.stripTrailingZeros().scale() > 3) {
                throw new IllegalArgumentException(key + " can have at most 3 decimal places");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    static boolean optionalBoolean(Map<String, Object> body, String key, boolean defaultValue) {
        String v = optionalString(body, key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }
}
