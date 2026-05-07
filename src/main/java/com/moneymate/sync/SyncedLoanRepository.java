package com.moneymate.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncedLoanRepository extends JpaRepository<SyncedLoan, String> {
    List<SyncedLoan> findByUserIdAndUpdatedAtGreaterThan(String userId, long since);
    Optional<SyncedLoan> findByUserIdAndClientId(String userId, Long clientId);
    Optional<SyncedLoan> findByUserIdAndSmsHash(String userId, String smsHash);
    void deleteAllByUserId(String userId);
}
