package com.artsyvibe.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paging info for templates (see templates/fragments/pager.html and static/admin-paging.js).
 * Page numbers are 1-based here and in the {@code ?page=} query parameter.
 */
public record PageView(int number, int totalPages, long total, long from, long to) {

    public static final int DEFAULT_SIZE = 20;

    public static PageView of(Page<?> page) {
        long from = page.getTotalElements() == 0 ? 0 : (long) page.getNumber() * page.getSize() + 1;
        long to = from == 0 ? 0 : from + page.getNumberOfElements() - 1;
        return new PageView(page.getNumber() + 1, Math.max(page.getTotalPages(), 1), page.getTotalElements(), from, to);
    }

    /** Builds a Pageable from a 1-based, possibly missing or out-of-range page number. */
    public static Pageable request(Integer page, Sort sort) {
        int index = page == null || page < 1 ? 0 : page - 1;
        return PageRequest.of(index, DEFAULT_SIZE, sort);
    }

    /** Normalises an optional text filter: "" when absent, otherwise trimmed. */
    public static String filter(String value) {
        return value == null ? "" : value.trim();
    }

    /** A LIKE pattern for case-insensitive "contains" search, or "" when there is no search. */
    public static String likePattern(String search) {
        String s = filter(search).toLowerCase();
        if (s.isEmpty()) {
            return "";
        }
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
