package com.moneymate.sms;

import com.moneymate.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        smsService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<SmsReportDto.ReportResult>> report(
            @AuthenticationPrincipal String userId,
            @RequestBody SmsReportDto.ReportRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(smsService.report(userId, req)));
    }
}
