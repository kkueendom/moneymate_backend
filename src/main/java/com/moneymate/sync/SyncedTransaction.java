package com.moneymate.sync;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "synced_transactions", indexes = {
        @Index(name = "idx_st_user_id",      columnList = "user_id"),
        @Index(name = "idx_st_deleted",      columnList = "is_deleted"),
        @Index(name = "idx_st_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_st_user_client",  columnList = "user_id, client_id", unique = true),
        @Index(name = "idx_st_user_sms",     columnList = "user_id, sms_hash",  unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncedTransaction {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Android Room local ID — unique per user */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** Server UUID of the linked account */
    @Column(name = "account_server_id", length = 36)
    private String accountServerId;

    @Column(nullable = false)
    private String amount;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(length = 20)
    private String type;

    @Column(name = "tx_status", length = 20)
    private String txStatus;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String merchant;

    @Column(columnDefinition = "TEXT")
    private String note;

    private String tags;

    @Column(name = "tx_date")
    private Long txDate;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Column(name = "category_key", length = 60)
    private String categoryKey;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean deleted = false;

    @Column(name = "sms_hash", length = 64)
    private String smsHash;
}
