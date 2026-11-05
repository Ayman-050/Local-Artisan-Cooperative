package com.example.demo.model;

/**
 * Values for {@code order_items.refund_status} (the artisan's decision) and
 * {@code order_items.admin_refund_status} (the admin's final decision).
 */
public final class RefundStatus {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DECLINED = "DECLINED";
    public static final String ESCALATED = "ESCALATED";
    /** Admin-only final state. */
    public static final String REJECTED = "REJECTED";

    private RefundStatus() {
    }
}
