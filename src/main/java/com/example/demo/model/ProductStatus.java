package com.example.demo.model;

/** Values stored in {@code products.status}. */
public final class ProductStatus {
    public static final String ACTIVE = "active";
    public static final String OUT_OF_STOCK = "out of stock";
    public static final String HIDDEN = "hidden";

    private ProductStatus() {
    }

    /** Status a visible product should have for the given stock level. */
    public static String forStock(int stockQuantity) {
        return stockQuantity > 0 ? ACTIVE : OUT_OF_STOCK;
    }
}
