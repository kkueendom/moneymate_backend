package com.moneymate.sync;

import com.moneymate.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/loans")
@RequiredArgsConstructor
public class LoanSyncController {

    private final LoanSyncService loanSyncService;

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<LoanSyncDto.PushResponse>> push(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody LoanSyncDto.PushRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(loanSyncService.push(userId, req)));
    }

    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<LoanSyncDto.PullResponse>> pull(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(loanSyncService.pull(userId, since, limit, page)));
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        loanSyncService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
