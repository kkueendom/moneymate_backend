package com.moneymate.auth;

import com.moneymate.common.BusinessException;
import com.moneymate.config.JwtUtil;
import com.moneymate.user.UserEntity;
import com.moneymate.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String OTP_PREFIX          = "otp:";
    private static final String OTP_TRIES           = "otp_tries:";
    private static final String PENDING_PREFIX      = "pending:";
    private static final String REFRESH_PREFIX      = "refresh:";
    private static final String REFRESH_SET_PREFIX  = "refresh_set:";
    private static final String JWT_BLACKLIST_PREFIX = "jwt_bl:";

    /**
     * Atomically: delete all old refresh tokens for this user, then insert the new one.
     * KEYS[1] = refresh_set:{userId}
     * ARGV[1] = "refresh:" prefix
     * ARGV[2] = full new token key (refresh:{newToken})
     * ARGV[3] = userId (value stored under the new key)
     * ARGV[4] = TTL in seconds
     * ARGV[5] = newToken (without prefix, added to the set)
     */
    private static final String BUILD_TOKENS_SCRIPT = """
        local oldTokens = redis.call('SMEMBERS', KEYS[1])
        for _, t in ipairs(oldTokens) do
            redis.call('DEL', ARGV[1] .. t)
        end
        redis.call('DEL', KEYS[1])
        redis.call('SET', ARGV[2], ARGV[3], 'EX', ARGV[4])
        redis.call('SADD', KEYS[1], ARGV[5])
        redis.call('EXPIRE', KEYS[1], ARGV[4])
        return 1
        """;

    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.expiry-seconds}")
    private long otpExpirySeconds;

    @Value("${app.otp.max-attempts}")
    private int maxOtpAttempts;

    @Value("${app.jwt.refresh-token-expiry-days}")
    private long refreshTokenExpiryDays;

    // OTP send limits: 5 per minute per IP, 3 per 10 minutes per phone number
    private static final int   OTP_IP_LIMIT        = 5;
    private static final long  OTP_IP_WINDOW_SEC   = 60;
    private static final int   OTP_PHONE_LIMIT     = 3;
    private static final long  OTP_PHONE_WINDOW_SEC = 600;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public void register(AuthDto.RegisterRequest req, String clientIp) {
        checkOtpRateLimit("otp:ip:" + clientIp, OTP_IP_LIMIT, OTP_IP_WINDOW_SEC);
        checkOtpRateLimit("otp:phone:" + req.getPhone(), OTP_PHONE_LIMIT, OTP_PHONE_WINDOW_SEC);

        if (userRepository.existsByPhone(req.getPhone())) {
            throw BusinessException.badRequest("Phone already registered");
        }

        // Store pending registration in Redis until OTP verified — format: passwordHash|name
        String passwordHash = passwordEncoder.encode(req.getPassword());
        String nameVal      = req.getName() != null ? req.getName() : "";
        redis.opsForValue().set(
                PENDING_PREFIX + req.getPhone(),
                passwordHash + "|" + nameVal,
                Duration.ofSeconds(otpExpirySeconds * 2));

        sendOtp(req.getPhone());
    }

    // ── Verify OTP (completes registration) ───────────────────────────────────

    @Transactional
    public AuthDto.TokenResponse verifyOtp(AuthDto.VerifyOtpRequest req) {
        validateOtp(req.getPhone(), req.getOtp());

        String pending = redis.opsForValue().get(PENDING_PREFIX + req.getPhone());
        if (pending == null) {
            throw BusinessException.badRequest("Registration session expired, please register again");
        }
        String[] parts        = pending.split("\\|", 2);
        String   passwordHash = parts[0];
        String   name         = parts.length > 1 ? parts[1] : "";

        UserEntity user = UserEntity.builder()
                .phone(req.getPhone())
                .passwordHash(passwordHash)
                .name(name.isBlank() ? null : name)
                .build();
        user = userRepository.save(user);

        redis.delete(PENDING_PREFIX + req.getPhone());
        return buildTokens(user);
    }

    // ── Login (phone or username + password) ──────────────────────────────────

    public AuthDto.TokenResponse login(AuthDto.LoginRequest req) {
        UserEntity user = userRepository.findByPhoneAndDeletedFalse(req.getPhone())
                .orElseThrow(() -> BusinessException.unauthorized("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("Invalid credentials");
        }

        return buildTokens(user);
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Transactional
    public AuthDto.TokenResponse refresh(AuthDto.RefreshRequest req) {
        String userId = redis.opsForValue().get(REFRESH_PREFIX + req.getRefreshToken());
        if (userId == null) {
            throw BusinessException.unauthorized("Refresh token expired or invalid");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.unauthorized("User not found"));

        // Remove the consumed token from the set before buildTokens clears the rest
        redis.opsForSet().remove(REFRESH_SET_PREFIX + userId, req.getRefreshToken());
        redis.delete(REFRESH_PREFIX + req.getRefreshToken());
        return buildTokens(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout(String refreshToken, String accessToken) {
        String userId = redis.opsForValue().get(REFRESH_PREFIX + refreshToken);
        redis.delete(REFRESH_PREFIX + refreshToken);
        if (userId != null) {
            redis.opsForSet().remove(REFRESH_SET_PREFIX + userId, refreshToken);
        }
        // Blacklist the access token's jti so it cannot be used even before its natural expiry.
        // JWT is stateless — without this, a stolen access token remains valid until TTL expires
        // even after the user explicitly logs out.
        if (accessToken != null && jwtUtil.isValid(accessToken)) {
            String jti = jwtUtil.getJti(accessToken);
            redis.opsForValue().set(
                    JWT_BLACKLIST_PREFIX + jti, "1",
                    Duration.ofMillis(jwtUtil.getAccessTokenExpiryMs()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private void sendOtp(String phone) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(OTP_PREFIX + phone, otp, Duration.ofSeconds(otpExpirySeconds));
        redis.delete(OTP_TRIES + phone);

        // TODO: integrate Twilio / Jazz SMS
        log.debug("[DEV] OTP generated for phone ending in ...{}", phone.length() > 4 ? phone.substring(phone.length() - 4) : "****");
    }

    private void validateOtp(String phone, String otp) {
        String triesKey = OTP_TRIES + phone;

        String stored = redis.opsForValue().get(OTP_PREFIX + phone);
        if (stored == null) {
            throw BusinessException.badRequest("OTP expired or not requested");
        }
        if (!stored.equals(otp)) {
            // INCR is atomic — increment first, then inspect the result.
            // Reading the counter before incrementing is a TOCTOU race: two concurrent wrong
            // attempts can both read the same count, both pass the < maxAttempts check, and the
            // counter under-counts by however many requests raced simultaneously.
            Long attempts = redis.opsForValue().increment(triesKey);
            redis.expire(triesKey, Duration.ofSeconds(otpExpirySeconds));
            if (attempts != null && attempts >= maxOtpAttempts) {
                throw BusinessException.tooManyRequests("Too many OTP attempts, please request a new OTP");
            }
            throw BusinessException.badRequest("Invalid OTP");
        }

        redis.delete(OTP_PREFIX + phone);
        redis.delete(triesKey);
    }

    // Redis INCR + EXPIRE rate limiter — works across restarts and multiple instances
    private void checkOtpRateLimit(String key, int limit, long windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > limit) {
            throw BusinessException.tooManyRequests("Too many OTP requests, please wait");
        }
    }

    private AuthDto.TokenResponse buildTokens(UserEntity user) {
        // Use a Lua script to atomically delete all old refresh tokens and insert the new one.
        // Without this, two concurrent logins can race: both read the old token set, both delete it,
        // then both insert their own new token — the second insertion deletes the first's token,
        // leaving the first device immediately logged out.
        String setKey = REFRESH_SET_PREFIX + user.getId();
        String newRefreshToken = UUID.randomUUID().toString();
        long expirySec = Duration.ofDays(refreshTokenExpiryDays).getSeconds();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(BUILD_TOKENS_SCRIPT, Long.class);
        redis.execute(script,
                List.of(setKey),
                REFRESH_PREFIX,
                REFRESH_PREFIX + newRefreshToken,
                user.getId(),
                String.valueOf(expirySec),
                newRefreshToken
        );

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getPhone());

        AuthDto.TokenResponse resp = new AuthDto.TokenResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(newRefreshToken);
        resp.setUserId(user.getId());
        resp.setPhone(user.getPhone());
        resp.setName(user.getName());
        return resp;
    }
}
