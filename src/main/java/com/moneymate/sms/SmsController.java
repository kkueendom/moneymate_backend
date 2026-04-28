package com.moneymate.sms;

import com.moneymate.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> report(
            @AuthenticationPrincipal String userId,
            @RequestBody SmsReportDto.ReportRequest req) {
        int saved = smsService.report(userId, req);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", saved)));
    }
}
