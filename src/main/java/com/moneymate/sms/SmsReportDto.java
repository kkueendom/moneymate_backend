package com.moneymate.sms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class SmsReportDto {

    @Data
    public static class ReportRequest {
        @NotNull
        @Size(max = 500, message = "Batch size cannot exceed 500 entries")
        private List<@Valid SmsEntry> entries;
    }

    @Data
    public static class SmsEntry {
        @NotBlank @Size(max = 64)  private String smsHash;
        @NotBlank @Size(max = 30)  private String sender;
        private long timestamp;
        @NotBlank @Size(max = 20)  private String classifiedAs;
        @DecimalMin("0")           private Double amount;
        @Size(max = 500)           private String body;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportResult {
        private int saved;
        private int skipped;
        private List<FailedEntry> failed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedEntry {
        private String smsHash;
        private String reason;
    }
}
