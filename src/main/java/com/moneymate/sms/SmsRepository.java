package com.moneymate.sms;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SmsRepository extends MongoRepository<SmsDocument, String> {
    boolean existsBySmsHashAndUserId(String smsHash, String userId);
    List<SmsDocument> findByUserId(String userId);
    void deleteAllByUserId(String userId);
}
