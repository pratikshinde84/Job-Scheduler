package com.jobscheduler.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sliding-window rate limiter backed by Upstash Redis.
 *
 * Each call to {@link #isAllowed} atomically checks and increments
 * a Redis sorted set that tracks request timestamps for a given key.
 * The Lua script ensures the check + insert is atomic — no race conditions.
 *
 * Key format:  rl:<userId>:<bucket>
 *   e.g.       rl:abc-123:default
 *              rl:abc-123:enqueue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowScript;

    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${ratelimit.default.limit:100}")
    private int defaultLimit;

    @Value("${ratelimit.default.window-seconds:60}")
    private int defaultWindowSeconds;

    @Value("${ratelimit.enqueue.limit:20}")
    private int enqueueLimit;

    @Value("${ratelimit.enqueue.window-seconds:60}")
    private int enqueueWindowSeconds;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Check whether the given user is within their rate limit for the bucket.
     *
     * @param userId  authenticated user identifier (UUID string)
     * @param bucket  logical bucket name — "default" or "enqueue"
     * @return        true if the request is allowed, false if limited
     */
    public boolean isAllowed(String userId, String bucket) {
        if (!enabled) return true;

        int limit         = resolveLimit(bucket);
        int windowSeconds = resolveWindow(bucket);
        long windowMs     = (long) windowSeconds * 1000;
        long nowMs        = System.currentTimeMillis();
        String key        = "rl:" + userId + ":" + bucket;

        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    List.of(key),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(limit));

            return result != null && result == 1L;

        } catch (Exception ex) {
            // If Redis is unreachable, fail open (allow the request) and log.
            // You can change this to fail closed (return false) if preferred.
            log.warn("RateLimiter: Redis error — failing open. key={} error={}", key, ex.getMessage());
            return true;
        }
    }

    /**
     * Returns the number of requests remaining in the current window.
     * Used to populate X-RateLimit-Remaining response header.
     */
    public long remaining(String userId, String bucket) {
        int limit         = resolveLimit(bucket);
        int windowSeconds = resolveWindow(bucket);
        long windowMs     = (long) windowSeconds * 1000;
        long windowStart  = System.currentTimeMillis() - windowMs;
        String key        = "rl:" + userId + ":" + bucket;

        try {
            Long used = redisTemplate.opsForZSet()
                    .countByScore(key, windowStart, Double.MAX_VALUE);
            long count = used != null ? used : 0;
            return Math.max(0, limit - count);
        } catch (Exception ex) {
            return limit; // fallback on error
        }
    }

    public int getLimit(String bucket)        { return resolveLimit(bucket); }
    public int getWindowSeconds(String bucket) { return resolveWindow(bucket); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveLimit(String bucket) {
        return "enqueue".equals(bucket) ? enqueueLimit : defaultLimit;
    }

    private int resolveWindow(String bucket) {
        return "enqueue".equals(bucket) ? enqueueWindowSeconds : defaultWindowSeconds;
    }
}
