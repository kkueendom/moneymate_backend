package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
public class UdharSyncService {

    private final SyncedUdharRepository udharRepo;

    // ── Push ─────────────────────────────────────────────────────────────────

    public UdharSyncDto.PushResponse push(String userId, UdharSyncDto.PushRequest req) {
        List<UdharSyncDto.ServerIdMapping> mappings = new ArrayList<>();
        List<Long> failedClientIds = new ArrayList<>();
        long now = System.currentTimeMillis();

        List<UdharSyncDto.UdharRecord> records =
                req.getUdhars() != null ? req.getUdhars() : Collections.emptyList();

        for (UdharSyncDto.UdharRecord rec : records) {
            try {
                UdharSyncDto.ServerIdMapping m = upsertRecord(userId, rec, now);
                if (m != null) mappings.add(m);
            } catch (Exception e) {
                log.error("Failed to upsert udhar clientId={} for userId={}", rec.getClientId(), userId, e);
                failedClientIds.add(rec.getClientId());
            }
        }

        UdharSyncDto.PushResponse resp = new UdharSyncDto.PushResponse();
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
    public UdharSyncDto.ServerIdMapping upsertRecord(String userId, UdharSyncDto.UdharRecord rec, long now) {
        SyncedUdhar entity = udharRepo
                .findByUserIdAndClientId(userId, rec.getClientId())
                .orElse(null);

        if (entity == null) {
            entity = SyncedUdhar.builder()
                    .userId(userId)
                    .clientId(rec.getClientId())
                    .build();
        } else if (rec.getUpdatedAt() != null && rec.getUpdatedAt() <= entity.getUpdatedAt()) {
            // Server version is newer — skip update but still return mapping
            UdharSyncDto.ServerIdMapping m = new UdharSyncDto.ServerIdMapping();
            m.setClientId(rec.getClientId());
            m.setServerId(entity.getId());
            return m;
        }

        entity.setDirection(rec.getDirection());
        entity.setPersonName(rec.getPersonName());
        entity.setPhone(rec.getPhone());
        entity.setAmount(rec.getAmount());
        entity.setCurrency(rec.getCurrency());
        entity.setPaidAmount(rec.getPaidAmount());
        entity.setLentDate(rec.getLentDate());
        entity.setDueDate(rec.getDueDate());
        entity.setNote(rec.getNote());
        entity.setStatus(rec.getStatus());
        entity.setCreatedAt(rec.getCreatedAt());
        entity.setUpdatedAt(rec.getUpdatedAt() != null ? rec.getUpdatedAt() : now);
        entity.setDeleted(rec.isDeleted());
        entity.setSmsHash(rec.getSmsHash());

        try {
            entity = udharRepo.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate push for udhar clientId={} or smsHash={}, fetching existing record",
                    rec.getClientId(), rec.getSmsHash());
            entity = udharRepo.findByUserIdAndClientId(userId, rec.getClientId())
                    .or(() -> rec.getSmsHash() != null
                            ? udharRepo.findByUserIdAndSmsHash(userId, rec.getSmsHash())
                            : java.util.Optional.empty())
                    .orElse(null);
            if (entity == null) return null;
        }

        UdharSyncDto.ServerIdMapping m = new UdharSyncDto.ServerIdMapping();
        m.setClientId(rec.getClientId());
        m.setServerId(entity.getId());
        return m;
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    public UdharSyncDto.PullResponse pull(String userId, long since, int limit, int page) {
        PageRequest pageable = PageRequest.of(page, limit, Sort.by("updatedAt").ascending());
        Page<SyncedUdhar> result = udharRepo.findByUserIdAndUpdatedAtGreaterThan(userId, since, pageable);
        List<SyncedUdhar> rows = result.getContent();

        List<UdharSyncDto.UdharRecord> records = rows.stream().map(e -> {
            UdharSyncDto.UdharRecord r = new UdharSyncDto.UdharRecord();
            r.setClientId(e.getClientId());
            r.setServerId(e.getId());
            r.setDirection(e.getDirection());
            r.setPersonName(e.getPersonName());
            r.setPhone(e.getPhone());
            r.setAmount(e.getAmount());
            r.setCurrency(e.getCurrency());
            r.setPaidAmount(e.getPaidAmount());
            r.setLentDate(e.getLentDate());
            r.setDueDate(e.getDueDate());
            r.setNote(e.getNote());
            r.setStatus(e.getStatus());
            r.setCreatedAt(e.getCreatedAt());
            r.setUpdatedAt(e.getUpdatedAt());
            r.setDeleted(Boolean.TRUE.equals(e.getDeleted()));
            r.setSmsHash(e.getSmsHash());
            return r;
        }).toList();

        UdharSyncDto.PullResponse resp = new UdharSyncDto.PullResponse();
        resp.setUdhars(records);
        resp.setServerTimestamp(System.currentTimeMillis());
        resp.setHasMore(result.hasNext());
        return resp;
    }

    // ── Delete All ───────────────────────────────────────────────────────────

    @Transactional
    public void deleteAll(String userId) {
        udharRepo.deleteAllByUserId(userId);
        log.info("Deleted all synced udhars for userId={}", userId);
    }
}
