package com.moneymate.sync;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncedTransactionRepository extends JpaRepository<SyncedTransaction, String> {
    List<SyncedTransaction> findByUserIdAndUpdatedAtGreaterThan(String userId, long since);
    Page<SyncedTransaction> findByUserIdAndUpdatedAtGreaterThan(String userId, long since, Pageable pageable);
    Optional<SyncedTransaction> findByUserIdAndClientId(String userId, Long clientId);
    Optional<SyncedTransaction> findByUserIdAndSmsHash(String userId, String smsHash);
    void deleteAllByUserId(String userId);
}
