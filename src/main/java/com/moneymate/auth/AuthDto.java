package com.moneymate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank
        @Pattern(regexp = "^\\+92[0-9]{10}$", message = "Phone must be a valid Pakistani number: +92XXXXXXXXXX")
        private String phone;

        @NotBlank
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        private String password;

        private String name;
    }

    @Data
    public static class VerifyOtpRequest {
        @NotBlank
        private String phone;

        @NotBlank
        @Size(min = 6, max = 6)
        private String otp;
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        @Pattern(regexp = "^\\+92[0-9]{10}$", message = "Phone must be a valid Pakistani number: +92XXXXXXXXXX")
        private String phone;

        @NotBlank
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String userId;
        private String phone;
        private String name;
        private boolean otherSessionsTerminated;
    }
}
