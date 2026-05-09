package com.moneymate.sync;

import com.moneymate.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<SyncDto.PushResponse>> push(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SyncDto.PushRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.push(userId, req)));
    }

    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<SyncDto.PullResponse>> pull(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.pull(userId, since, limit, page)));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SyncDto.StatusResponse>> status(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.status(userId)));
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        syncService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
