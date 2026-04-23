package com.moneymate.user;

import com.moneymate.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto.ProfileResponse>> getProfile(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto.ProfileResponse>> updateProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UserDto.UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, req)));
    }

    @DeleteMapping("/data")
    public ResponseEntity<ApiResponse<Void>> deleteData(
            @AuthenticationPrincipal String userId) {
        userService.deleteData(userId);
        return ResponseEntity.ok(ApiResponse.ok("All user data deleted", null));
    }
}
