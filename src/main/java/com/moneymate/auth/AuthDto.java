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
        @Size(min = 4, max = 4, message = "PIN must be exactly 4 digits")
        @Pattern(regexp = "^[0-9]{4}$", message = "PIN must be numeric")
        private String pin;

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
        private String phone;

        @NotBlank
        @Size(min = 4, max = 4)
        private String pin;
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
    }
}
