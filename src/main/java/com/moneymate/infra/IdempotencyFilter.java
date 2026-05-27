package com.moneymate.infra;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Server-side idempotency enforcement for POST endpoints.
 *
 * ─── WHY THIS IS NECESSARY ───────────────────────────────────────────────────
 *   Mobile clients on flaky networks (3G in Pakistan) frequently retry requests
 *   when they don't get a response within the timeout.  Without server-side
 *   idempotency that retry inserts the same 50 transactions twice.
 *   This filter makes retries safe: the second call returns the exact same
 *   response as the first, without re-running any business logic.
 *
 *   This is the same mechanism used by Stripe, Braintree, and most financial APIs.
 *
 * ─── HOW IT WORKS ────────────────────────────────────────────────────────────
 *   1. Client generates a UUID before sending a POST and attaches it as
 *      the Idempotency-Key header.  The same UUID is sent on every retry.
 *
 *   2. CACHE HIT (retry scenario):
 *      Filter looks up "idem:{userId}:{key}" in Redis.
 *      If found → write the stored JSON directly to the response, return.
 *      The handler is never invoked.  No DB writes, no double-inserts.
 *
 *   3. CACHE MISS (first call):
 *      Filter wraps the HttpServletResponse in a ContentCachingResponseWrapper,
 *      which buffers the response body as it's written by the handler.
 *      After the handler completes, the buffered body is stored in Redis with a
 *      24-hour TTL, then flushed to the real response.
 *
 * ─── KEY DESIGN DECISIONS ────────────────────────────────────────────────────
 *   • Per-user isolation: key = "idem:{userId}:{idempotencyKey}"
 *     Prevents user-A from accidentally replaying user-B's response if they
 *     happened to choose the same UUID (astronomically unlikely, but correct).
 *
 *   • Only 200 OK responses are cached.
 *     A 400 validation error or 500 is not idempotent — the client should fix
 *     the request and retry with a new key.
 *
 *   • Only POST requests are intercepted.
 *     GET/DELETE are already idempotent by HTTP semantics.
 *
 *   • ContentCachingResponseWrapper buffers the body in memory.
 *     copyBodyToResponse() MUST be called to flush the buffer to the real socket.
 *     Forgetting this causes the client to receive an empty body.
 *
 * ─── REGISTRATION ────────────────────────────────────────────────────────────
 *   Registered inside Spring Security's filter chain via SecurityConfig
 *   (.addFilterAfter(idempotencyFilter, JwtAuthFilter.class)) so that
 *   SecurityContextHolder already holds the authenticated user when this runs.
 *   A FilterRegistrationBean disables the default servlet-container registration
 *   to prevent the filter from running twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final String PREFIX = "idem:";
    private static final Duration TTL  = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String idempKey = req.getHeader(HEADER);

        // Only intercept POST requests that carry the header
        if (!"POST".equalsIgnoreCase(req.getMethod()) || idempKey == null || idempKey.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        // Scope the Redis key to the authenticated user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anon";
        String redisKey = PREFIX + userId + ":" + idempKey;

        // ── CACHE HIT: replay the first response verbatim ────────────────────
        String cached = redis.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("[Idempotency] Cache hit — replaying stored response | key={} userId={}",
                    idempKey, userId);
            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(cached);
            return; // handler is never invoked
        }

        // ── CACHE MISS: capture this response for future replays ──────────────
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(res);
        chain.doFilter(req, wrapper); // pass wrapper downstream so the body is buffered

        if (wrapper.getStatus() == HttpServletResponse.SC_OK) {
            byte[] bodyBytes = wrapper.getContentAsByteArray();
            if (bodyBytes.length > 0) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                redis.opsForValue().set(redisKey, body, TTL);
                log.info("[Idempotency] Cached response | key={} userId={} ttl=24h",
                        idempKey, userId);
            }
        }

        // CRITICAL: flush the buffered body to the actual response socket
        wrapper.copyBodyToResponse();
    }
}
