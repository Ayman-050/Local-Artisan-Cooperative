package com.artsyvibe.security;

import java.io.Serializable;

/**
 * The principal stored in the HTTP session after login. Deliberately tiny: anything that can change
 * (name, status, email) is re-read from the database through {@link CurrentUser}.
 */
public record AuthenticatedUser(Long userId, String role) implements Serializable {
}
