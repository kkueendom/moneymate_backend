package com.moneymate.auth;

import com.moneymate.common.BusinessException;
import com.moneymate.config.JwtUtil;
import com.moneymate.user.UserEntity;
import com.moneymate.user.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String OTP_PREFIX      = "otp:";
    private static final String OTP_TRIES       = "otp_tries:";
    private static final String PENDING_PREFIX  = "pending:";
    private static final String REFRESH_PREFIX  = "refresh:";

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

    @Value("${app.rate-limit.otp-per-minute}")
    private int otpPerMinute;

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public void register(AuthDto.RegisterRequest req, String clientIp) {
        checkOtpRateLimit(clientIp);

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

        redis.delete(REFRESH_PREFIX + req.getRefreshToken());
        return buildTokens(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout(String refreshToken) {
        redis.delete(REFRESH_PREFIX + refreshToken);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendOtp(String phone) {
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        redis.opsForValue().set(OTP_PREFIX + phone, otp, Duration.ofSeconds(otpExpirySeconds));
        redis.delete(OTP_TRIES + phone);

        // TODO: integrate Twilio / Jazz SMS
        log.info("[DEV] OTP for {}: {}", phone, otp);
    }

    private void validateOtp(String phone, String otp) {
        String triesKey = OTP_TRIES + phone;
        String attempts = redis.opsForValue().get(triesKey);
        if (attempts != null && Integer.parseInt(attempts) >= maxOtpAttempts) {
            throw BusinessException.tooManyRequests("Too many OTP attempts, please request a new OTP");
        }

        String stored = redis.opsForValue().get(OTP_PREFIX + phone);
        if (stored == null) {
            throw BusinessException.badRequest("OTP expired or not requested");
        }
        if (!stored.equals(otp)) {
            redis.opsForValue().increment(triesKey);
            redis.expire(triesKey, Duration.ofSeconds(otpExpirySeconds));
            throw BusinessException.badRequest("Invalid OTP");
        }

        redis.delete(OTP_PREFIX + phone);
        redis.delete(triesKey);
    }

    private void checkOtpRateLimit(String clientIp) {
        Bucket bucket = ipBuckets.computeIfAbsent(clientIp, ip ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(otpPerMinute)
                                .refillGreedy(otpPerMinute, Duration.ofMinutes(1))
                                .build())
                        .build());

        if (!bucket.tryConsume(1)) {
            throw BusinessException.tooManyRequests("Too many OTP requests, please wait");
        }
    }

    private AuthDto.TokenResponse buildTokens(UserEntity user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getPhone());
        String refreshToken = UUID.randomUUID().toString();
        redis.opsForValue().set(
                REFRESH_PREFIX + refreshToken,
                user.getId(),
                Duration.ofDays(refreshTokenExpiryDays));

        AuthDto.TokenResponse resp = new AuthDto.TokenResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setUserId(user.getId());
        resp.setPhone(user.getPhone());
        resp.setName(user.getName());
        return resp;
    }
}
