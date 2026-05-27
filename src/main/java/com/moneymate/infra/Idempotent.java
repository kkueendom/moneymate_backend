package com.moneymate.infra;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as idempotent.
 *
 * When a client includes an {@code Idempotency-Key} header on a POST request to this endpoint,
 * the first successful response is cached in Redis for {@link #ttlSeconds()} seconds.
 * Any subsequent POST with the same key returns the cached response immediately without
 * re-executing the business logic — safe for the client to retry on network failure.
 *
 * This annotation is enforced by {@link IdempotencyFilter}.
 *
 * Usage:
 * <pre>
 *   {@literal @}PostMapping("/push")
 *   {@literal @}Idempotent
 *   public ResponseEntity<...> push(...) { ... }
 * </pre>
 *
 * Client usage:
 * <pre>
 *   POST /api/v2/sync/push
 *   Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * How long the cached response is kept in Redis.
     * Default 24 hours matches Stripe's idempotency window.
     */
    int ttlSeconds() default 86_400;
}
