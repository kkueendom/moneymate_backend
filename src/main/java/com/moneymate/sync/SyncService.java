package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final String SYNC_TS_PREFIX = "sync_ts:";

    private final SyncedTransactionRepository txRepo;
    private final StringRedisTemplate redis;

    // ── Push ─────────────────────────────────────────────────────────────────

    @Transactional
    public SyncDto.PushResponse push(String userId, SyncDto.PushRequest req) {
        List<SyncDto.ServerIdMapping> mappings = new ArrayList<>();
        long now = System.currentTimeMillis();

        List<SyncDto.TransactionRecord> records =
                req.getTransactions() != null ? req.getTransactions() : Collections.emptyList();

        for (SyncDto.TransactionRecord rec : records) {
            SyncedTransaction entity = txRepo
                    .findByUserIdAndClientId(userId, rec.getClientId())
                    .orElse(null);

            if (entity == null) {
                // New record from client
                entity = SyncedTransaction.builder()
                        .userId(userId)
                        .clientId(rec.getClientId())
                        .build();
            } else if (rec.getUpdatedAt() != null && rec.getUpdatedAt() <= entity.getUpdatedAt()) {
                // Server version is newer — skip but still return mapping
                SyncDto.ServerIdMapping m = new SyncDto.ServerIdMapping();
                m.setClientId(rec.getClientId());
                m.setServerId(entity.getId());
                mappings.add(m);
                continue;
            }

            // Upsert fields (Last-Write-Wins: client sends a newer updatedAt)
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
            mappings.add(m);
        }

        redis.opsForValue().set(SYNC_TS_PREFIX + userId,
                String.valueOf(now), Duration.ofDays(90));

        SyncDto.PushResponse resp = new SyncDto.PushResponse();
        resp.setIdMappings(mappings);
        resp.setServerTimestamp(now);
        return resp;
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    public SyncDto.PullResponse pull(String userId, long since) {
        List<SyncedTransaction> rows = txRepo.findByUserIdAndUpdatedAtGreaterThan(userId, since);

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
        return resp;
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
