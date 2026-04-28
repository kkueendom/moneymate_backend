package com.moneymate.sms;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SmsRepository extends MongoRepository<SmsDocument, String> {
    boolean existsBySmsHash(String smsHash);
    List<SmsDocument> findByUserId(String userId);
}
