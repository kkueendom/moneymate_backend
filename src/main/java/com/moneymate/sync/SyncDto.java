package com.moneymate.sync;

import lombok.Data;

import java.util.List;

public class SyncDto {

    @Data
    public static class TransactionRecord {
        private Long   clientId;
        private String serverId;
        private String accountServerId;
        private String amount;
        private String direction;
        private String type;
        private String txStatus;
        private String description;
        private String merchant;
        private String note;
        private String tags;
        private Long   txDate;
        private Long   createdAt;
        private Long   updatedAt;
        private String categoryKey;
        private boolean deleted;
        private String smsHash;
    }

    @Data
    public static class ServerIdMapping {
        private Long   clientId;
        private String serverId;
    }

    @Data
    public static class PushRequest {
        private List<TransactionRecord> transactions;
        private long clientTimestamp;
    }

    @Data
    public static class PushResponse {
        private List<ServerIdMapping> idMappings;
        private long serverTimestamp;
    }

    @Data
    public static class PullResponse {
        private List<TransactionRecord> transactions;
        private long serverTimestamp;
        private boolean hasMore;
    }

    @Data
    public static class StatusResponse {
        private long   lastSyncAt;
        private int    pendingCount;
        private String status;
    }
}
