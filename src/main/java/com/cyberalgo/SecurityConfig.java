package com.cyberalgo;

import java.util.Optional;

/**
 * Centralized security configuration.
 * Administrator bootstrap is opt-in and never resets an existing password.
 */
public final class SecurityConfig {
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;
    public static final String TEMP_DEFAULT_ADMIN_USERNAME = "admin";
    public static final String TEMP_DEFAULT_ADMIN_PASSWORD = "admin";

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

    /**
     * Temporary development fallback requested for the hidden admin portal.
     * Explicit environment/JVM credentials still take precedence.
     */
    public static AdminBootstrap effectiveAdminBootstrap() {
        return adminBootstrap().orElseGet(() ->
                new AdminBootstrap(TEMP_DEFAULT_ADMIN_USERNAME, TEMP_DEFAULT_ADMIN_PASSWORD));
    }

    public static boolean isTemporaryDefault(AdminBootstrap bootstrap) {
        return TEMP_DEFAULT_ADMIN_USERNAME.equals(bootstrap.username())
                && TEMP_DEFAULT_ADMIN_PASSWORD.equals(bootstrap.password())
                && adminBootstrap().isEmpty();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
