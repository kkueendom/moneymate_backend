package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private static final String SYNC_TS_PREFIX = "sync_ts:";

    private final SyncedTransactionRepository txRepo;
    private final StringRedisTemplate redis;

    // ── Push ─────────────────────────────────────────────────────────────────

    public SyncDto.PushResponse push(String userId, SyncDto.PushRequest req) {
        List<SyncDto.ServerIdMapping> mappings = new ArrayList<>();
        List<Long> failedClientIds = new ArrayList<>();
        long now = System.currentTimeMillis();

        List<SyncDto.TransactionRecord> records =
                req.getTransactions() != null ? req.getTransactions() : Collections.emptyList();

        for (SyncDto.TransactionRecord rec : records) {
            try {
                SyncDto.ServerIdMapping m = upsertRecord(userId, rec, now);
                if (m != null) mappings.add(m);
            } catch (Exception e) {
                log.error("Failed to upsert transaction clientId={} for userId={}", rec.getClientId(), userId, e);
                failedClientIds.add(rec.getClientId());
            }
        }

        redis.opsForValue().set(SYNC_TS_PREFIX + userId,
                String.valueOf(now), Duration.ofDays(90));

        SyncDto.PushResponse resp = new SyncDto.PushResponse();
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
    public SyncDto.ServerIdMapping upsertRecord(String userId, SyncDto.TransactionRecord rec, long now) {
        // 1. Look up by (userId, clientId) — normal path
        SyncedTransaction entity = txRepo
                .findByUserIdAndClientId(userId, rec.getClientId())
                .orElse(null);

        // 2. Not found by clientId — check smsHash BEFORE attempting INSERT to avoid
        //    DataIntegrityViolationException that corrupts the Hibernate Session.
        //    This happens when the user logs out, Room is cleared, and re-imports the
        //    same SMS (new clientIds, same smsHash).
        if (entity == null && rec.getSmsHash() != null) {
            SyncedTransaction existing = txRepo.findByUserIdAndSmsHash(userId, rec.getSmsHash()).orElse(null);
            if (existing != null) {
                log.debug("Re-login dedup: smsHash={} already exists, returning serverId for clientId {}",
                        rec.getSmsHash(), rec.getClientId());
                SyncDto.ServerIdMapping m = new SyncDto.ServerIdMapping();
                m.setClientId(rec.getClientId());
                m.setServerId(existing.getId());
                return m;
            }
        }

        // 3. Truly new record — build entity
        if (entity == null) {
            entity = SyncedTransaction.builder()
                    .userId(userId)
                    .clientId(rec.getClientId())
                    .build();
        } else if (rec.getUpdatedAt() != null && rec.getUpdatedAt() <= entity.getUpdatedAt()) {
            // Server version is newer — skip but still return mapping
            SyncDto.ServerIdMapping m = new SyncDto.ServerIdMapping();
            m.setClientId(rec.getClientId());
            m.setServerId(entity.getId());
            return m;
        }

        entity.setAccountServerId(rec.getAccountServerId());
        entity.setAmount(rec.getAmount());
        entity.setDirection(rec.getDirection());
        entity.setType(rec.getType());
        entity.setTxStatus(rec.getTxStatus());
        entity.setDescription(rec.getDescription());
        entity.setMerchant(rec.getMerchant());
        entity.setNote(rec.getNote());
        entity.setTags(rec.getTags());
        entity.setTxDate(rec.getTxDate());
        entity.setCreatedAt(rec.getCreatedAt());
        entity.setUpdatedAt(rec.getUpdatedAt() != null ? rec.getUpdatedAt() : now);
        entity.setCategoryKey(rec.getCategoryKey());
        entity.setDeleted(rec.isDeleted());
        entity.setSmsHash(rec.getSmsHash());

        entity = txRepo.save(entity);

        SyncDto.ServerIdMapping m = new SyncDto.ServerIdMapping();
        m.setClientId(rec.getClientId());
        m.setServerId(entity.getId());
        return m;
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    public SyncDto.PullResponse pull(String userId, long since, int limit, int page) {
        PageRequest pageable = PageRequest.of(page, limit, Sort.by("updatedAt").ascending());
        Page<SyncedTransaction> result = txRepo.findByUserIdAndUpdatedAtGreaterThan(userId, since, pageable);
        List<SyncedTransaction> rows = result.getContent();

        List<SyncDto.TransactionRecord> records = rows.stream().map(e -> {
            SyncDto.TransactionRecord r = new SyncDto.TransactionRecord();
            r.setClientId(e.getClientId());
            r.setServerId(e.getId());
            r.setAccountServerId(e.getAccountServerId());
            r.setAmount(e.getAmount());
            r.setDirection(e.getDirection());
            r.setType(e.getType());
            r.setTxStatus(e.getTxStatus());
            r.setDescription(e.getDescription());
            r.setMerchant(e.getMerchant());
            r.setNote(e.getNote());
            r.setTags(e.getTags());
            r.setTxDate(e.getTxDate());
            r.setCreatedAt(e.getCreatedAt());
            r.setUpdatedAt(e.getUpdatedAt());
            r.setCategoryKey(e.getCategoryKey());
            r.setDeleted(Boolean.TRUE.equals(e.getDeleted()));
            r.setSmsHash(e.getSmsHash());
            return r;
        }).toList();

        SyncDto.PullResponse resp = new SyncDto.PullResponse();
        resp.setTransactions(records);
        resp.setServerTimestamp(System.currentTimeMillis());
        resp.setHasMore(result.hasNext());
        return resp;
    }

    // ── Delete All ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteAll(String userId) {
        txRepo.deleteAllByUserId(userId);
        // Redis is not transactional — delete only after MySQL commits successfully
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redis.delete(SYNC_TS_PREFIX + userId);
                }
            }
        );
        log.info("Deleted all synced transactions for userId={}", userId);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public SyncDto.StatusResponse status(String userId) {
        String ts = redis.opsForValue().get(SYNC_TS_PREFIX + userId);
        SyncDto.StatusResponse resp = new SyncDto.StatusResponse();
        resp.setLastSyncAt(ts != null ? Long.parseLong(ts) : 0L);
        resp.setPendingCount(0);
        resp.setStatus("OK");
        return resp;
    }
}
