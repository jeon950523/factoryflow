package com.factoryflow.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A bounded, single-JVM sliding-window admission limit for login POSTs.
 * Attempts are reserved BEFORE BCrypt executes to avoid simultaneous-request bypasses.
 * Both username and transport-peer address limits are enforced independently.
 */
public final class LoginAttemptLimiter {
    static final int USER_LIMIT = 8;
    static final int PEER_LIMIT = 120;
    static final long WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final int MAX_BUCKETS = 20_000;
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{3,31}");

    private final Clock clock;
    private final Map<String, ArrayDeque<Long>> attempts = new HashMap<>();
    private long lastCleanup;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
        this.lastCleanup = clock.millis();
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision allowedNow() { return new Decision(true, 0); }
    }

    /**
     * Atomically checks and reserves BOTH slots, so parallel login requests share a hard limit.
     * Only authenticated success clears the username bucket; peer buckets remain enforced.
     */
    public synchronized Decision reserve(String username, String transportPeer) {
        long now = clock.millis();
        String usernameKey = userKey(username);
        String peerKey = peerKey(transportPeer);
        if (now - lastCleanup >= 60_000 || attempts.size() >= MAX_BUCKETS - 2) {
            cleanup(now);
        }

        ArrayDeque<Long> userWindow = attempts.get(usernameKey);
        ArrayDeque<Long> peerWindow = attempts.get(peerKey);
        if (userWindow != null) prune(userWindow, now);
        if (peerWindow != null) prune(peerWindow, now);

        long retryUser = retrySeconds(userWindow, USER_LIMIT, now);
        long retryPeer = retrySeconds(peerWindow, PEER_LIMIT, now);
        if (retryUser > 0 || retryPeer > 0) {
            return new Decision(false, Math.max(retryUser, retryPeer));
        }

        int newKeys = (userWindow == null ? 1 : 0) + (peerWindow == null ? 1 : 0);
        if (attempts.size() + newKeys > MAX_BUCKETS) {
            // Fail closed under high-cardinality attacks, rather than allowing unbounded memory.
            return new Decision(false, Duration.ofMillis(WINDOW_MILLIS).toSeconds());
        }
        if (userWindow == null) {
            userWindow = new ArrayDeque<>();
            attempts.put(usernameKey, userWindow);
        }
        if (peerWindow == null) {
            peerWindow = new ArrayDeque<>();
            attempts.put(peerKey, peerWindow);
        }
        userWindow.addLast(now);
        peerWindow.addLast(now);
        return Decision.allowedNow();
    }

    public synchronized void authenticated(String username) {
        attempts.remove(userKey(username));
    }

    private void cleanup(long now) {
        Iterator<Map.Entry<String, ArrayDeque<Long>>> entries = attempts.entrySet().iterator();
        while (entries.hasNext()) {
            ArrayDeque<Long> window = entries.next().getValue();
            prune(window, now);
            if (window.isEmpty()) entries.remove();
        }
        lastCleanup = now;
    }

    private static void prune(ArrayDeque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
            window.removeFirst();
        }
    }

    private static long retrySeconds(ArrayDeque<Long> window, int limit, long now) {
        if (window == null || window.size() < limit) return 0;
        return Math.max(1, (window.peekFirst() + WINDOW_MILLIS - now + 999) / 1_000);
    }

    private static String userKey(String username) {
        String normalized = username == null ? "" : username.toLowerCase(Locale.ROOT);
        return "user:" + (VALID_USERNAME.matcher(normalized).matches() ? normalized : "invalid");
    }

    private static String peerKey(String transportPeer) {
        // Deliberately ignore request headers such as X-Forwarded-For (client-controlled unless
        // a separately verified trusted-proxy configuration rewrites them).
        if (transportPeer == null || transportPeer.isBlank() || transportPeer.length() > 64) {
            return "peer:unknown";
        }
        return "peer:" + transportPeer;
    }
}
