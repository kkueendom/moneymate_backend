package com.moneymate.sms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Compound unique index replaces two separate indexes:
// - Queries by userId are covered by the leading column
// - Dedup is per-user (same SMS hash from two different users is valid)
@CompoundIndex(name = "idx_user_hash", def = "{'userId': 1, 'smsHash': 1}", unique = true)
@Document(collection = "sms_reports")
public class SmsDocument {

    @Id
    private String id;

    private String userId;

    private String smsHash;

    private String sender;
    private long   timestamp;
    private String classifiedAs;
    private Double amount;
    private String body;
    private LocalDateTime receivedAt;
}
