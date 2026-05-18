package com.moneymate.sms;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SmsRepository extends MongoRepository<SmsDocument, String> {
    boolean existsBySmsHashAndUserId(String smsHash, String userId);
    List<SmsDocument> findByUserId(String userId);
    void deleteAllByUserId(String userId);

    /** Returns only the smsHash field for matching docs — used for batch dedup. */
    @Query(value = "{ 'userId': ?0, 'smsHash': { $in: ?1 } }", fields = "{ 'smsHash': 1 }")
    List<SmsDocument> findHashesByUserIdAndSmsHashIn(String userId, Collection<String> smsHashes);
}
