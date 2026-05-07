package com.moneymate.sms;

import lombok.Data;
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
}
