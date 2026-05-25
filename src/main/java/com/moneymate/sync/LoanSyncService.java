package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        List<Long> failedClientIds = new ArrayList<>();
        long now = System.currentTimeMillis();

        List<LoanSyncDto.LoanRecord> records =
                req.getLoans() != null ? req.getLoans() : Collections.emptyList();

        for (LoanSyncDto.LoanRecord rec : records) {
            try {
                LoanSyncDto.ServerIdMapping m = upsertRecord(userId, rec, now);
                if (m != null) mappings.add(m);
            } catch (Exception e) {
                log.error("Failed to upsert loan clientId={} for userId={}", rec.getClientId(), userId, e);
                failedClientIds.add(rec.getClientId());
            }
        }

        LoanSyncDto.PushResponse resp = new LoanSyncDto.PushResponse();
        resp.setIdMappings(mappings);
        resp.setFailedClientIds(failedClientIds.isEmpty() ? null : failedClientIds);
        resp.setServerTimestamp(now);
        return resp;
    }

    /**
     * Each record runs in its own REQUIRES_NEW transaction so a duplicate-key
     * violation only rolls back that single record, not the entire push batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanSyncDto.ServerIdMapping upsertRecord(String userId, LoanSyncDto.LoanRecord rec, long now) {
        // 1. Look up by (userId, clientId) — normal path
        SyncedLoan entity = loanRepo
                .findByUserIdAndClientId(userId, rec.getClientId())
                .orElse(null);

        // 2. Not found by clientId — check serverId next.
        //    After logout+login, Room is cleared and pulled records get new local IDs.
        //    When the user edits such a record, the push sends the new clientId but still
        //    carries the original serverId — use it to find and update the existing record
        //    instead of creating a duplicate.
        if (entity == null && rec.getServerId() != null) {
            SyncedLoan byServerId = loanRepo.findById(rec.getServerId()).orElse(null);
            if (byServerId != null && byServerId.getUserId().equals(userId)) {
                log.debug("Re-login clientId migration: serverId={} found, updating clientId {} -> {}",
                        rec.getServerId(), byServerId.getClientId(), rec.getClientId());
                byServerId.setClientId(rec.getClientId());
                entity = byServerId;
            }
        }

        // 3. Not found by clientId or serverId — dedup by smsHash to avoid a stale-import
        //    creating a second record for the same SMS after logout+login.
        if (entity == null && rec.getSmsHash() != null) {
            SyncedLoan existing = loanRepo.findByUserIdAndSmsHash(userId, rec.getSmsHash()).orElse(null);
            if (existing != null) {
                log.debug("Re-login dedup: smsHash={} already exists, returning serverId for clientId {}",
                        rec.getSmsHash(), rec.getClientId());
                LoanSyncDto.ServerIdMapping m = new LoanSyncDto.ServerIdMapping();
                m.setClientId(rec.getClientId());
                m.setServerId(existing.getId());
                return m;
            }
        }

        // 4. Truly new record
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

        entity = loanRepo.save(entity);

        LoanSyncDto.ServerIdMapping m = new LoanSyncDto.ServerIdMapping();
        m.setClientId(rec.getClientId());
        m.setServerId(entity.getId());
        return m;
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    public LoanSyncDto.PullResponse pull(String userId, long since, int limit, int page) {
        PageRequest pageable = PageRequest.of(page, limit, Sort.by("updatedAt").ascending());
        Page<SyncedLoan> result = loanRepo.findByUserIdAndUpdatedAtGreaterThan(userId, since, pageable);
        List<SyncedLoan> rows = result.getContent();

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
        resp.setHasMore(result.hasNext());
        return resp;
    }

    // ── Delete All ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteAll(String userId) {
        loanRepo.deleteAllByUserId(userId);
        log.info("Deleted all synced loans for userId={}", userId);
    }
}
