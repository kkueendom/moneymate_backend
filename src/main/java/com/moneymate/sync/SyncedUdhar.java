package com.moneymate.sync;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "synced_udhars", indexes = {
        @Index(name = "idx_su_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_su_user_client",  columnList = "user_id, client_id", unique = true),
        @Index(name = "idx_su_user_sms",     columnList = "user_id, sms_hash",  unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncedUdhar {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Android Room local ID — unique per user */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "person_name", nullable = false)
    private String personName;

    private String phone;

    @Column(nullable = false)
    private String amount;

    @Column(length = 10)
    private String currency;

    @Column(name = "paid_amount")
    private String paidAmount;

    @Column(name = "lent_date")
    private Long lentDate;

    @Column(name = "due_date")
    private Long dueDate;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean deleted = false;

    @Column(name = "sms_hash", length = 64)
    private String smsHash;
}
