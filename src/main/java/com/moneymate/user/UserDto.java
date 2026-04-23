package com.moneymate.user;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Value;

public class UserDto {

    @Data
    public static class UpdateRequest {
        @Size(max = 100)
        private String name;

        private String avatarUrl;
    }

    @Value
    public static class ProfileResponse {
        String id;
        String phone;
        String name;
        String avatarUrl;
        String createdAt;
    }
}
