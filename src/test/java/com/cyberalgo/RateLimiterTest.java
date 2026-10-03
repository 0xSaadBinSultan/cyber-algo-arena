package com.cyberalgo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void blocksRequestsAfterConfiguredWindowLimit() {
        RateLimiter limiter = new RateLimiter();
        String key = "unit-test-login";

        assertTrue(limiter.allow(key, 2, 60_000L));
        assertTrue(limiter.allow(key, 2, 60_000L));
        assertFalse(limiter.allow(key, 2, 60_000L));
    }
}
