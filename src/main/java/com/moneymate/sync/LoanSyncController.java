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
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
public class LoanSyncController {

    private final LoanSyncService loanSyncService;
    private final RateLimitService rateLimitService;

    private static final int  SYNC_LIMIT  = 60;
    private static final long SYNC_WINDOW = 60L;

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<LoanSyncDto.PushResponse>> push(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody LoanSyncDto.PushRequest req) {
        rateLimitService.check("rl:loans:push:" + userId, SYNC_LIMIT, SYNC_WINDOW);
        return ResponseEntity.ok(ApiResponse.ok(loanSyncService.push(userId, req)));
    }

    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<LoanSyncDto.PullResponse>> pull(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") int page) {
        rateLimitService.check("rl:loans:pull:" + userId, SYNC_LIMIT, SYNC_WINDOW);
        return ResponseEntity.ok(ApiResponse.ok(loanSyncService.pull(userId, since, limit, page)));
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        loanSyncService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
