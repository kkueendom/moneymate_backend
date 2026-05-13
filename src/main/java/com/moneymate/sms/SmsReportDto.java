package com.moneymate.sms;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class SmsReportDto {

    @Data
    public static class ReportRequest {
        private List<SmsEntry> entries;
    }

    @Data
    public static class SmsEntry {
        private String smsHash;
        private String sender;
        private long timestamp;
        private String classifiedAs;   // LEDGER / BILL / LOAN / UDHAR
        private Double amount;
        private String body;
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
