package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanSyncService {

    private final SyncedLoanRepository loanRepo;

    // ── Push ─────────────────────────────────────────────────────────────────

    public LoanSyncDto.PushResponse push(String userId, LoanSyncDto.PushRequest req) {
        List<LoanSyncDto.ServerIdMapping> mappings = new ArrayList<>();
        long now = System.currentTimeMillis();

        List<LoanSyncDto.LoanRecord> records =
                req.getLoans() != null ? req.getLoans() : Collections.emptyList();

        for (LoanSyncDto.LoanRecord rec : records) {
            LoanSyncDto.ServerIdMapping m = upsertRecord(userId, rec, now);
            if (m != null) mappings.add(m);
        }

        LoanSyncDto.PushResponse resp = new LoanSyncDto.PushResponse();
        resp.setIdMappings(mappings);
        resp.setServerTimestamp(now);
        return resp;
    }

    /**
     * Each record runs in its own REQUIRES_NEW transaction so a duplicate-key
     * violation only rolls back that single record, not the entire push batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanSyncDto.ServerIdMapping upsertRecord(String userId, LoanSyncDto.LoanRecord rec, long now) {
        SyncedLoan entity = loanRepo
                .findByUserIdAndClientId(userId, rec.getClientId())
                .orElse(null);

        if (entity == null) {
            entity = SyncedLoan.builder()
                    .userId(userId)
                    .clientId(rec.getClientId())
                    .build();
        } else if (rec.getUpdatedAt() != null && rec.getUpdatedAt() <= entity.getUpdatedAt()) {
            // Server version is newer — skip update but still return mapping
            LoanSyncDto.ServerIdMapping m = new LoanSyncDto.ServerIdMapping();
            m.setClientId(rec.getClientId());
            m.setServerId(entity.getId());
            return m;
        }

        entity.setPlatform(rec.getPlatform());
        entity.setLoanType(rec.getLoanType());
        entity.setTotalAmount(rec.getTotalAmount());
        entity.setPaidAmount(rec.getPaidAmount());
        entity.setInterestRate(rec.getInterestRate());
        entity.setInstallments(rec.getInstallments());
        entity.setDueDay(rec.getDueDay());
        entity.setNextDueDate(rec.getNextDueDate());
        entity.setStatus(rec.getStatus());
        entity.setNote(rec.getNote());
        entity.setCreatedAt(rec.getCreatedAt());
        entity.setUpdatedAt(rec.getUpdatedAt() != null ? rec.getUpdatedAt() : now);
        entity.setDeleted(rec.isDeleted());
        entity.setSmsHash(rec.getSmsHash());

        try {
            entity = loanRepo.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate push for loan clientId={} or smsHash={}, fetching existing record",
                    rec.getClientId(), rec.getSmsHash());
            entity = loanRepo.findByUserIdAndClientId(userId, rec.getClientId())
                    .or(() -> rec.getSmsHash() != null
                            ? loanRepo.findByUserIdAndSmsHash(userId, rec.getSmsHash())
                            : java.util.Optional.empty())
                    .orElse(null);
            if (entity == null) return null;
        }

        LoanSyncDto.ServerIdMapping m = new LoanSyncDto.ServerIdMapping();
        m.setClientId(rec.getClientId());
        m.setServerId(entity.getId());
        return m;
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    public LoanSyncDto.PullResponse pull(String userId, long since) {
        List<SyncedLoan> rows = loanRepo.findByUserIdAndUpdatedAtGreaterThan(userId, since);

        List<LoanSyncDto.LoanRecord> records = rows.stream().map(e -> {
            LoanSyncDto.LoanRecord r = new LoanSyncDto.LoanRecord();
            r.setClientId(e.getClientId());
            r.setServerId(e.getId());
            r.setPlatform(e.getPlatform());
            r.setLoanType(e.getLoanType());
            r.setTotalAmount(e.getTotalAmount());
            r.setPaidAmount(e.getPaidAmount());
            r.setInterestRate(e.getInterestRate());
            r.setInstallments(e.getInstallments());
            r.setDueDay(e.getDueDay());
            r.setNextDueDate(e.getNextDueDate());
            r.setStatus(e.getStatus());
            r.setNote(e.getNote());
            r.setCreatedAt(e.getCreatedAt());
            r.setUpdatedAt(e.getUpdatedAt());
            r.setDeleted(Boolean.TRUE.equals(e.getDeleted()));
            r.setSmsHash(e.getSmsHash());
            return r;
        }).toList();

        LoanSyncDto.PullResponse resp = new LoanSyncDto.PullResponse();
        resp.setLoans(records);
        resp.setServerTimestamp(System.currentTimeMillis());
        return resp;
    }

    // ── Delete All ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteAll(String userId) {
        loanRepo.deleteAllByUserId(userId);
        log.info("Deleted all synced loans for userId={}", userId);
    }
}
