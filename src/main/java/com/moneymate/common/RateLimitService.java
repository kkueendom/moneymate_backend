package com.moneymate.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis INCR-based sliding-window rate limiter, shared across controllers.
 * Same strategy as OTP rate limiting in AuthService — works correctly
 * across multiple app instances and survives restarts.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;

    /**
     * @param key           unique Redis key (e.g. "rl:sync:push:&lt;userId&gt;")
     * @param limit         max requests allowed within the window
     * @param windowSeconds window duration in seconds
     * @throws BusinessException HTTP 429 when limit is exceeded
     */
    public void check(String key, int limit, long windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > limit) {
            throw BusinessException.tooManyRequests("Too many requests, please slow down");
        }
    }
}
