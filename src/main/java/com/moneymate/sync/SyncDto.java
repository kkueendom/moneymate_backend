package com.moneymate.sync;

import lombok.Data;

import java.util.List;
import java.util.Map;

public class SyncDto {

    @Data
    public static class PushRequest {
        private List<Map<String, Object>> transactions;
        private List<Map<String, Object>> bills;
        private List<Map<String, Object>> loans;
        private List<Map<String, Object>> udhar;
        private long clientTimestamp;
    }

    @Data
    public static class PullResponse {
        private List<Map<String, Object>> transactions;
        private List<Map<String, Object>> bills;
        private List<Map<String, Object>> loans;
        private List<Map<String, Object>> udhar;
        private long serverTimestamp;
    }

    @Data
    public static class StatusResponse {
        private long lastSyncAt;
        private int pendingCount;
        private String status;   // OK / PENDING / ERROR
    }
}
