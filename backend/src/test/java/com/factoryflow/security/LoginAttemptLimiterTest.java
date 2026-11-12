package com.factoryflow.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {
    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(1_000_000L);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        @Override public long millis() { return millis.get(); }
        void advance(long deltaMillis) { millis.addAndGet(deltaMillis); }
    }

    @Test
    void usernameLimitCannotBeBypassedByChangingAddressOrLetterCase() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(new MutableClock());
        for (int i = 0; i < LoginAttemptLimiter.USER_LIMIT; i++) {
            assertTrue(limiter.reserve("ManagerA", "192.0.2." + i).allowed());
        }
        LoginAttemptLimiter.Decision blocked = limiter.reserve("managera", "192.0.2.250");
        assertFalse(blocked.allowed());
        assertEquals(300, blocked.retryAfterSeconds());
    }

    @Test
    void peerLimitStopsUsernameSpraying() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(new MutableClock());
        for (int i = 0; i < LoginAttemptLimiter.PEER_LIMIT; i++) {
            assertTrue(limiter.reserve("user" + i, "192.0.2.9").allowed());
        }
        assertFalse(limiter.reserve("lastUser", "192.0.2.9").allowed());
        assertTrue(limiter.reserve("lastUser", "192.0.2.10").allowed());
    }

    @Test
    void expiredWindowAllowsLoginAgainWithoutSleeping() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < LoginAttemptLimiter.USER_LIMIT; i++) {
            assertTrue(limiter.reserve("account1", "192.0.2.9").allowed());
        }
        clock.advance(299_001);
        assertEquals(1, limiter.reserve("account1", "192.0.2.9").retryAfterSeconds());
        clock.advance(999);
        assertTrue(limiter.reserve("account1", "192.0.2.9").allowed());
    }

    @Test
    void successfulLoginClearsOnlyAccountBucketButNotSourceFloodProtection() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(new MutableClock());
        for (int i = 0; i < LoginAttemptLimiter.USER_LIMIT; i++) {
            assertTrue(limiter.reserve("account1", "192.0.2.9").allowed());
        }
        assertFalse(limiter.reserve("account1", "192.0.2.9").allowed());
        limiter.authenticated("account1");
        assertTrue(limiter.reserve("account1", "192.0.2.9").allowed());
        for (int i = LoginAttemptLimiter.USER_LIMIT + 1; i < LoginAttemptLimiter.PEER_LIMIT; i++) {
            assertTrue(limiter.reserve("other" + i, "192.0.2.9").allowed());
        }
        assertFalse(limiter.reserve("extraUser", "192.0.2.9").allowed());
    }

    @Test
    void simultaneousAdmissionsCannotExceedUsernameLimit() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(new MutableClock());
        AtomicInteger admitted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 40; i++) {
                final int index = i;
                jobs.add(executor.submit(() -> {
                    start.await();
                    if (limiter.reserve("account1", "192.0.2." + index).allowed()) {
                        admitted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertEquals(LoginAttemptLimiter.USER_LIMIT, admitted.get());
    }

    @Test
    void malformedNamesShareBoundedInvalidUsernameBucket() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(new MutableClock());
        for (int i = 0; i < LoginAttemptLimiter.USER_LIMIT; i++) {
            assertTrue(limiter.reserve("!" + i, "192.0.2." + i).allowed());
        }
        assertFalse(limiter.reserve("!infinite-random-spam", "192.0.2.250").allowed());
    }
}
