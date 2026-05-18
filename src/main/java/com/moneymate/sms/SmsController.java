package com.moneymate.sms;

import com.moneymate.common.ApiResponse;
import com.moneymate.common.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;
    private final RateLimitService rateLimitService;

    // SMS report is a batch operation (onboarding scan + occasional real-time uploads)
    // 10 calls per hour per user is generous for normal usage
    private static final int  SMS_REPORT_LIMIT  = 10;
    private static final long SMS_REPORT_WINDOW = 3600L;

    @DeleteMapping("/deleteAll")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @AuthenticationPrincipal String userId) {
        smsService.deleteAll(userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<SmsReportDto.ReportResult>> report(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SmsReportDto.ReportRequest req) {
        rateLimitService.check("rl:sms:report:" + userId, SMS_REPORT_LIMIT, SMS_REPORT_WINDOW);
        return ResponseEntity.ok(ApiResponse.ok(smsService.report(userId, req)));
    }
}
