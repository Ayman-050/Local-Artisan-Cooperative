package com.example.demo.model;

/** Values stored in {@code users.role}. Spring Security authorities are {@code "ROLE_" + value}. */
public final class Roles {
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ARTISAN = "ARTISAN";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
