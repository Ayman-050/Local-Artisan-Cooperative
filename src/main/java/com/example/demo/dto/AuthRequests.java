package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies for {@code /api/auth/**}. Unknown JSON fields (e.g. a legacy {@code email}) are ignored. */
public final class AuthRequests {

    /** Same rule the registration form enforces client-side. */
    public static final String PASSWORD_RULE = "^(?=.*[A-Z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_RULE_MESSAGE =
            "Password must be 8-72 characters and include an uppercase letter and a number.";
    public static final String PHONE_RULE = "^\\+?[0-9 ]{7,20}$";

    private AuthRequests() {
    }

    public record Login(
            @NotBlank(message = "Email is required.") String email,
            @NotBlank(message = "Password is required.") String password) {
    }

    public record Register(
            @NotBlank(message = "Name is required.") @Size(max = 100, message = "Name is too long.") String name,
            @NotBlank(message = "Email is required.") @Email(message = "Please enter a valid email address.")
            @Size(max = 255) String email,
            @NotBlank(message = "Please enter a valid phone number.")
            @Pattern(regexp = PHONE_RULE, message = "Please enter a valid phone number.") String phone,
            @NotBlank(message = "Password is required.")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_RULE_MESSAGE) String password,
            // Artisan-only fields
            @Size(max = 255, message = "Shop name is too long.") String shopName,
            @Size(max = 255, message = "Biography is too long (255 characters max).") String biography,
            String profilePicture) {
    }

    public record ChangePassword(
            @NotBlank(message = "Missing required fields") String oldPassword,
            @NotBlank(message = "Missing required fields")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_RULE_MESSAGE) String newPassword) {
    }

    public record DeleteAccount(@NotBlank(message = "Please enter your password to confirm.") String password) {
    }
}
