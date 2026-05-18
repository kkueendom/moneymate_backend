package com.moneymate.sync;

import com.moneymate.common.ApiResponse;
import com.moneymate.common.RateLimitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v2/udhars")
@RequiredArgsConstructor
public class UdharSyncController {

    private final UdharSyncService udharSyncService;
    private final RateLimitService rateLimitService;

    private static final int  SYNC_LIMIT  = 60;
    private static final long SYNC_WINDOW = 60L;

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<UdharSyncDto.PushResponse>> push(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UdharSyncDto.PushRequest req) {
        rateLimitService.check("rl:udhars:push:" + userId, SYNC_LIMIT, SYNC_WINDOW);
        return ResponseEntity.ok(ApiResponse.ok(udharSyncService.push(userId, req)));
    }

    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<UdharSyncDto.PullResponse>> pull(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") int page) {
        rateLimitService.check("rl:udhars:pull:" + userId, SYNC_LIMIT, SYNC_WINDOW);
        return ResponseEntity.ok(ApiResponse.ok(udharSyncService.pull(userId, since, limit, page)));
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        udharSyncService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
