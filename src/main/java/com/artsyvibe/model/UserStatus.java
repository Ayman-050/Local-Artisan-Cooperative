package com.artsyvibe.model;

/** Values stored in {@code users.status}. Compared case-insensitively because legacy rows vary in case. */
public final class UserStatus {
    public static final String ACTIVE = "active";
    public static final String PENDING = "pending";
    /** Legacy spelling of {@link #PENDING}; still produced by "reconsider". */
    public static final String PENDING_APPROVAL = "pending approval";
    public static final String SUSPENDED = "suspended";
    public static final String REJECTED = "rejected";
    /** Account closed; personal data has been anonymised (see AccountService). */
    public static final String DELETED = "deleted";

    private UserStatus() {
    }

    public static boolean isActive(String status) {
        return ACTIVE.equalsIgnoreCase(status);
    }

    public static boolean isPending(String status) {
        return PENDING.equalsIgnoreCase(status) || PENDING_APPROVAL.equalsIgnoreCase(status);
    }
}
