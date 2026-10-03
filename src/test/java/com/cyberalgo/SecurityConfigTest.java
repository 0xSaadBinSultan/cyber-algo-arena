package com.cyberalgo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @AfterEach
    void cleanupProperties() {
        System.clearProperty("arena.admin.username");
        System.clearProperty("arena.admin.password");
    }

    @Test
    void rejectsFormerDefaultAdministratorPassword() {
        System.setProperty("arena.admin.username", "admin");
        System.setProperty("arena.admin.password", "admin_password_123");

        assertThrows(IllegalStateException.class, SecurityConfig::adminBootstrap);
    }

    @Test
    void acceptsExplicitStrongBootstrapPassword() {
        System.setProperty("arena.admin.username", "arena-admin");
        System.setProperty("arena.admin.password", "UniqueAdminPassword!2026");

        SecurityConfig.AdminBootstrap bootstrap = SecurityConfig.adminBootstrap().orElseThrow();

        assertEquals("arena-admin", bootstrap.username());
        assertEquals("UniqueAdminPassword!2026", bootstrap.password());
    }
}
