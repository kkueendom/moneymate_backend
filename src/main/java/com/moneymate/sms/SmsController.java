package com.moneymate.sms;

import com.moneymate.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v2/sms")
@RequiredArgsConstructor
public class SmsController {

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal String userId,
            @RequestBody SmsReportDto.ReportRequest req) {
        // Phase C will persist anonymised SMS metadata for analytics.
        log.info("SMS report from user {}: {} entries", userId,
                req.getEntries() != null ? req.getEntries().size() : 0);
        return ResponseEntity.ok(ApiResponse.ok("Received", null));
    }
}
