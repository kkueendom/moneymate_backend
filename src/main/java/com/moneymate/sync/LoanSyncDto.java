package com.moneymate.sync;

import lombok.Data;

import java.util.List;

public class LoanSyncDto {

    @Data
    public static class LoanRecord {
        private Long    clientId;
        private String  serverId;
        private String  platform;
        private String  loanType;
        private String  totalAmount;
        private String  paidAmount;
        private Double  interestRate;
        private Integer installments;
        private Integer dueDay;
        private Long    nextDueDate;
        private String  status;
        private String  note;
        private Long    createdAt;
        private Long    updatedAt;
        private boolean deleted;
        private String  smsHash;
    }

    @Data
    public static class ServerIdMapping {
        private Long   clientId;
        private String serverId;
    }

    @Data
    public static class PushRequest {
        private List<LoanRecord> loans;
        private long clientTimestamp;
    }

    @Data
    public static class PushResponse {
        private List<ServerIdMapping> idMappings;
        private long serverTimestamp;
    }

    @Data
    public static class PullResponse {
        private List<LoanRecord> loans;
        private long serverTimestamp;
    }
}
