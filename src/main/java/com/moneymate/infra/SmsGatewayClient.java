package com.moneymate.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * HTTP client for the internal message-platform SMS gateway.
 *
 * Two operations are supported:
 *   - sendOtp:   POST /message/commit  — triggers the platform to generate and send an OTP SMS
 *   - verifyOtp: POST /verifycode/check — validates the code the user typed in
 *
 * The platform owns the OTP lifecycle; we only store the tokenId in Redis so the
 * verify call can reference the same send event.
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
     * @param phone   recipient's mobile number
     * @param tokenId UUID that links this send to the subsequent verify call
     * @param flowNo  idempotency key (deduplicated by the platform for ~30 s)
     * @throws SmsGatewayException if the platform returns a non-zero code or the call fails
     */
    public void sendOtp(String phone, String tokenId, String flowNo) {
        Map<String, Object> body = Map.of(
                "callerId",    callerId,
                "mobile",      phone,
                "templateCode", templateCode,
                "tokenId",     tokenId,
                "flowNo",      flowNo
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
     * Verify the OTP code the user submitted.
     *
     * @return true if the platform returns code == 0 (verification passed)
     */
    public boolean verifyOtp(String phone, String tokenId, String inputCode) {
        Map<String, Object> body = Map.of(
                "callerId",    callerId,
                "mobile",      phone,
                "templateCode", templateCode,
                "tokenId",     tokenId,
                "inputCode",   inputCode
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
