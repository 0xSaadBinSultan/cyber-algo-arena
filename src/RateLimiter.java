import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding-window rate limiter and consecutive failure cooldown engine.
 */
public final class RateLimiter {

    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFailureTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    /**
     * Evaluates sliding window request rate.
     * @param key client identifier (e.g. IP address or teamId)
     * @param maxRequests maximum requests permitted in window
     * @param windowMillis sliding window duration in milliseconds
     * @return true if permitted, false if rate exceeded (HTTP 429)
     */
    public synchronized boolean allow(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;

        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Purge expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    /**
     * Records a failed attempt for consecutive penalty tracking.
     */
    public synchronized void recordFailure(String key) {
        lastFailureTimes.put(key, System.currentTimeMillis());
        consecutiveFailures.merge(key, 1, Integer::sum);
    }

    /**
     * Resets failure counter upon successful operation.
     */
    public synchronized void resetFailures(String key) {
        lastFailureTimes.remove(key);
        consecutiveFailures.remove(key);
    }

    /**
     * Checks if cooldown period is active following consecutive failures.
     * @param key identifier
     * @param minFailuresThreshold minimum consecutive failures to trigger cooldown
     * @param cooldownMillis duration of cooldown in milliseconds
     * @return true if currently locked under cooldown
     */
    public synchronized boolean isCooldownActive(String key, int minFailuresThreshold, long cooldownMillis) {
        int failures = consecutiveFailures.getOrDefault(key, 0);
        if (failures < minFailuresThreshold) {
            return false;
        }

        Long lastFail = lastFailureTimes.get(key);
        if (lastFail == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastFail;
        return elapsed < cooldownMillis;
    }

    public synchronized long getRemainingCooldownSeconds(String key, long cooldownMillis) {
        Long lastFail = lastFailureTimes.get(key);
        if (lastFail == null) return 0;
        long elapsed = System.currentTimeMillis() - lastFail;
        long remaining = cooldownMillis - elapsed;
        return remaining > 0 ? (remaining + 999) / 1000 : 0;
    }
}
