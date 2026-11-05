package com.example.demo.model;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Values stored in {@code orders.status} and {@code order_items.fulfilment_status}.
 * An order in status {@link #CART} is the customer's open cart.
 *
 * <p>Each order line is fulfilled by the artisan who sells it ({@code OrderItem.fulfilmentStatus}); the
 * order's own status is derived from its lines with {@link #aggregate(Collection)}.
 */
public final class OrderStatus {
    public static final String CART = "cart";
    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String SHIPPED = "shipped";
    public static final String DELIVERED = "delivered";
    public static final String CANCELLED = "cancelled";

    /** Fulfilment steps in order. */
    private static final List<String> STEPS = List.of(PENDING, PROCESSING, SHIPPED, DELIVERED);

    /**
     * Statuses an artisan or admin may move items/orders to. CANCELLED is deliberately excluded: cancelling
     * would have to restock items and refund payment, which is not implemented.
     */
    public static final Set<String> FULFILMENT_STATUSES = Set.copyOf(STEPS);

    private OrderStatus() {
    }

    private static int rank(String status) {
        int i = status == null ? -1 : STEPS.indexOf(status.toLowerCase(Locale.ROOT));
        return Math.max(i, 0);
    }

    /**
     * Overall status of an order from the statuses of its lines: the least advanced line wins
     * (an order is only "shipped" when every artisan has shipped), except that an order where some
     * artisans have started but others have not counts as "processing".
     */
    public static String aggregate(Collection<String> lineStatuses) {
        if (lineStatuses.isEmpty()) {
            return PENDING;
        }
        int min = lineStatuses.stream().mapToInt(OrderStatus::rank).min().orElse(0);
        int max = lineStatuses.stream().mapToInt(OrderStatus::rank).max().orElse(0);
        if (min == 0 && max > 0) {
            return PROCESSING;
        }
        return STEPS.get(min);
    }
}
