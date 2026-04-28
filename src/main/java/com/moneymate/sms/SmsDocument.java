package com.moneymate.sms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sms_reports")
public class SmsDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String smsHash;

    private String sender;
    private long   timestamp;
    private String classifiedAs;
    private Double amount;
    private LocalDateTime receivedAt;
}
