package com.moneymate.infra;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * HTTP client for the SMS gateway, hardened with Circuit Breaker + Retry.
 *
 * ─── WHY CIRCUIT BREAKER? ────────────────────────────────────────────────────
 *   Without protection, if the SMS gateway goes down every one of the (many)
 *   concurrent registration/login requests waits for the full HTTP timeout
 *   (e.g. 30s) before failing.  Under load this exhausts the thread pool and
 *   brings down the entire service — a "cascading failure".
 *
 *   A Circuit Breaker is a state machine that short-circuits calls to a
 *   failing dependency:
 *
 *     CLOSED  ──(≥50% failures in last 10 calls)──►  OPEN
 *       ▲                                               │
 *       │                                               │ wait 30s
 *       │                                               ▼
 *       └────(≥2/3 probes succeed)────────────  HALF-OPEN
 *
 *   While OPEN, calls fail instantly with a user-friendly message instead of
 *   timing out.  This protects our thread pool and gives the SMS gateway time
 *   to recover.
 *
 * ─── WHY RETRY? ──────────────────────────────────────────────────────────────
 *   ~5% of SMS gateway calls fail due to transient network hiccups.
 *   Retrying once after 500ms resolves the majority of these without the user
 *   ever seeing an error.  We don't retry indefinitely — just once — to keep
 *   the latency budget reasonable for a registration flow.
 *
 * ─── COMPOSITION ORDER ───────────────────────────────────────────────────────
 *   Resilience4j applies decorators outside-in.  The default aspect order is:
 *     Retry → CircuitBreaker → method call
 *   This means: each retry attempt goes through the circuit breaker.
 *   If the circuit is OPEN, the retry immediately receives CallNotPermittedException
 *   and stops retrying — correct behaviour (no point retrying an open circuit).
 */
@Slf4j
@Component
public class SmsGatewayClient {

    private final RestClient restClient;

    @Value("${app.sms.caller-id}")
    private int callerId;

    @Value("${app.sms.template-code}")
    private String templateCode;

    public SmsGatewayClient(@Value("${app.sms.gateway-url}") String gatewayUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(gatewayUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Ask the platform to generate an OTP and deliver it to {@code phone}.
     *
     * Decorated with @Retry (1 retry on network error) and @CircuitBreaker
     * (opens after 50% failure rate, fallback to user-friendly error).
     */
    @CircuitBreaker(name = "sms-gateway", fallbackMethod = "sendOtpFallback")
    @Retry(name = "sms-gateway")
    public void sendOtp(String phone, String tokenId, String flowNo) {
        Map<String, Object> body = Map.of(
                "callerId",     callerId,
                "mobile",       phone,
                "templateCode", templateCode,
                "tokenId",      tokenId,
                "flowNo",       flowNo
        );

        GatewayResponse resp;
        try {
            resp = restClient.post()
                    .uri("/message/commit")
                    .body(body)
                    .retrieve()
                    .body(GatewayResponse.class);
        } catch (RestClientException e) {
            log.error("[SMS] Network error sending OTP to phone ending {}: {}",
                    masked(phone), e.getMessage());
            throw new SmsGatewayException("SMS service unavailable, please try again later");
        }

        if (resp == null || resp.getCode() != 0) {
            String msg = resp != null ? resp.getMessage() : "null response";
            log.error("[SMS] Platform rejected OTP send for phone ending {}: code={} msg={}",
                    masked(phone), resp != null ? resp.getCode() : -1, msg);
            throw new SmsGatewayException("Failed to send OTP: " + msg);
        }

        log.info("[SMS] OTP sent to phone ending {}, messageId={}, tokenId={}",
                masked(phone), resp.getData(), tokenId);
    }

    /**
     * Fallback invoked when either:
     *   - All retries are exhausted (SmsGatewayException after 2 attempts), OR
     *   - The circuit is OPEN (CallNotPermittedException — fast-fail, no network call made).
     *
     * Signature rule: same method name + "Fallback", same parameters, plus a Throwable at end.
     */
    private void sendOtpFallback(String phone, String tokenId, String flowNo, Throwable ex) {
        if (ex instanceof CallNotPermittedException) {
            log.warn("[SMS] Circuit OPEN — fast-failing OTP send for phone ending {}. " +
                     "Gateway will be retried in 30s.", masked(phone));
        } else {
            log.error("[SMS] All retries exhausted for phone ending {}: {}", masked(phone), ex.getMessage());
        }
        throw new SmsGatewayException("SMS service is temporarily unavailable. Please try again shortly.");
    }

    /**
     * Verify the OTP code the user submitted.
     * Also protected by circuit breaker — a verify call to a down gateway should fail fast.
     */
    @CircuitBreaker(name = "sms-gateway", fallbackMethod = "verifyOtpFallback")
    public boolean verifyOtp(String phone, String tokenId, String inputCode) {
        Map<String, Object> body = Map.of(
                "callerId",     callerId,
                "mobile",       phone,
                "templateCode", templateCode,
                "tokenId",      tokenId,
                "inputCode",    inputCode
        );

        GatewayResponse resp;
        try {
            resp = restClient.post()
                    .uri("/verifycode/check")
                    .body(body)
                    .retrieve()
                    .body(GatewayResponse.class);
        } catch (RestClientException e) {
            log.error("[SMS] Network error verifying OTP: {}", e.getMessage());
            throw new SmsGatewayException("SMS service unavailable, please try again later");
        }

        if (resp == null) {
            log.error("[SMS] Null response from verify endpoint");
            throw new SmsGatewayException("SMS service unavailable, please try again later");
        }

        return resp.getCode() == 0;
    }

    private boolean verifyOtpFallback(String phone, String tokenId, String inputCode, Throwable ex) {
        log.warn("[SMS] Circuit OPEN — fast-failing OTP verify for phone ending {}", masked(phone));
        throw new SmsGatewayException("SMS service is temporarily unavailable. Please try again shortly.");
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** Unified response shape used by both /message/commit and /verifycode/check */
    public static class GatewayResponse {
        private int code;
        private String message;
        private String data;

        public int    getCode()    { return code; }
        public String getMessage() { return message; }
        public String getData()    { return data; }
        public void setCode(int code)          { this.code = code; }
        public void setMessage(String message) { this.message = message; }
        public void setData(String data)       { this.data = data; }
    }

    public static class SmsGatewayException extends RuntimeException {
        public SmsGatewayException(String message) { super(message); }
    }

    private static String masked(String phone) {
        return phone.length() > 4 ? phone.substring(phone.length() - 4) : "****";
    }
}
