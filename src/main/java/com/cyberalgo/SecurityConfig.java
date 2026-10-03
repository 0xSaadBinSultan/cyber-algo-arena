package com.cyberalgo;

import java.util.Optional;

/**
 * Centralized security configuration.
 * Administrator bootstrap is opt-in and never resets an existing password.
 */
public final class SecurityConfig {
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private SecurityConfig() {
    }

    public record AdminBootstrap(String username, String password) {
    }

    public static Optional<AdminBootstrap> adminBootstrap() {
        String password = firstNonBlank(
                System.getProperty("arena.admin.password"),
                System.getenv("ARENA_ADMIN_PASSWORD"));

        if (password == null) {
            return Optional.empty();
        }

        String username = firstNonBlank(
                System.getProperty("arena.admin.username"),
                System.getenv("ARENA_ADMIN_USERNAME"));
        if (username == null) {
            username = "admin";
        }

        validateBootstrapPassword(username, password);
        return Optional.of(new AdminBootstrap(username.trim(), password));
    }

    static void validateBootstrapPassword(String username, String password) {
        if (password == null || password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ARENA_ADMIN_PASSWORD must contain at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters.");
        }

        String normalized = password.trim();
        if (normalized.equalsIgnoreCase("admin")
                || normalized.equalsIgnoreCase("admin123")
                || normalized.equals("admin_password_123")
                || normalized.equalsIgnoreCase(username == null ? "" : username.trim())) {
            throw new IllegalStateException("ARENA_ADMIN_PASSWORD is too weak; choose a unique administrator password.");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
