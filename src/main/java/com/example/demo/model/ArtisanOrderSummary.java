package com.example.demo.model;

import java.math.BigDecimal;

/**
 * One row of an artisan's order list: the order, its product names, the artisan's share of the total, and the
 * fulfilment status of the artisan's own lines (which can differ from the order's overall status).
 */
public record ArtisanOrderSummary(Order order, String productNames, BigDecimal totalAmount, String status) {
}
