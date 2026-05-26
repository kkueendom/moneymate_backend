package com.moneymate.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis INCR-based sliding-window rate limiter, shared across controllers.
 * Uses a Lua script so INCR and EXPIRE are one atomic operation — prevents a server crash
 * between the two calls from leaving a key with no TTL (counter stuck at limit forever).
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;

    // Lua: increment counter; set TTL only on first increment (count == 1).
    // Atomicity ensures no crash window between INCR and EXPIRE.
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
        new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /**
     * @param key           unique Redis key (e.g. "rl:sync:push:&lt;userId&gt;")
     * @param limit         max requests allowed within the window
     * @param windowSeconds window duration in seconds
     * @throws BusinessException HTTP 429 when limit is exceeded
     */
    public void check(String key, int limit, long windowSeconds) {
        Long count = redis.execute(RATE_LIMIT_SCRIPT, List.of(key), String.valueOf(windowSeconds));
        if (count != null && count > limit) {
            throw BusinessException.tooManyRequests("Too many requests, please slow down");
        }
    }
}
