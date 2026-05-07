package com.moneymate.sync;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "synced_loans", indexes = {
        @Index(name = "idx_sl_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_sl_user_client",  columnList = "user_id, client_id", unique = true),
        @Index(name = "idx_sl_user_sms",     columnList = "user_id, sms_hash",  unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncedLoan {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Android Room local ID — unique per user */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "loan_type", length = 20)
    private String loanType;

    @Column(name = "total_amount", nullable = false)
    private String totalAmount;

    @Column(name = "paid_amount")
    private String paidAmount;

    @Column(name = "interest_rate")
    private Double interestRate;

    private Integer installments;

    @Column(name = "due_day")
    private Integer dueDay;

    @Column(name = "next_due_date")
    private Long nextDueDate;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String note;

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
