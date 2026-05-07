package com.moneymate.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncedUdharRepository extends JpaRepository<SyncedUdhar, String> {
    List<SyncedUdhar> findByUserIdAndUpdatedAtGreaterThan(String userId, long since);
    Optional<SyncedUdhar> findByUserIdAndClientId(String userId, Long clientId);
    Optional<SyncedUdhar> findByUserIdAndSmsHash(String userId, String smsHash);
    void deleteAllByUserId(String userId);
}
