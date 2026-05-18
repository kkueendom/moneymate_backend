package com.moneymate.sync;

import lombok.Data;

import java.util.List;

public class UdharSyncDto {

    @Data
    public static class UdharRecord {
        private Long    clientId;
        private String  serverId;
        private String  direction;
        private String  personName;
        private String  phone;
        private String  amount;
        private String  currency;
        private String  paidAmount;
        private Long    lentDate;
        private Long    dueDate;
        private String  note;
        private String  status;
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
        private List<UdharRecord> udhars;
        private long clientTimestamp;
    }

    @Data
    public static class PushResponse {
        private List<ServerIdMapping> idMappings;
        private List<Long> failedClientIds;
        private long serverTimestamp;
    }

    @Data
    public static class PullResponse {
        private List<UdharRecord> udhars;
        private long serverTimestamp;
        private boolean hasMore;
    }
}
