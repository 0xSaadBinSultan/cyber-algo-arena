package com.cyberalgo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserSecurityTest {

    @Test
    void hardCodedAdminAliasesAreNotAccepted() {
        String realPassword = "StrongAdminPassword!2026";
        User admin = new User(
                "U-ADMIN-TEST",
                "admin",
                "admin@test.local",
                User.hashPassword(realPassword),
                User.Role.ADMIN,
                null);

        assertTrue(admin.verifyPassword(realPassword));
        assertFalse(admin.verifyPassword("admin"));
        assertFalse(admin.verifyPassword("admin123"));
        assertFalse(admin.verifyPassword("admin_password_123"));
    }

    @Test
    void wrongPasswordIsRejectedForPlayer() {
        User player = new User(
                "U-PLAYER-TEST",
                "alice",
                "alice@test.local",
                User.hashPassword("CorrectHorseBatteryStaple"),
                User.Role.PLAYER,
                null);

        assertTrue(player.verifyPassword("CorrectHorseBatteryStaple"));
        assertFalse(player.verifyPassword("wrong-password"));
    }
}
