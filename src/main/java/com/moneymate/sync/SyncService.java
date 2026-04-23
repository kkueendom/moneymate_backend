package com.moneymate.sync;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final String SYNC_TS_PREFIX = "sync_ts:";

    private final StringRedisTemplate redis;

    public SyncDto.PullResponse pull(String userId, long since) {
        // Phase C will implement real delta pull from DB.
        // Returns empty payload — Android client can merge gracefully.
        SyncDto.PullResponse resp = new SyncDto.PullResponse();
        resp.setTransactions(Collections.emptyList());
        resp.setBills(Collections.emptyList());
        resp.setLoans(Collections.emptyList());
        resp.setUdhar(Collections.emptyList());
        resp.setServerTimestamp(System.currentTimeMillis());
        return resp;
    }

    public void push(String userId, SyncDto.PushRequest req) {
        // Phase C will implement real upsert logic.
        // Record last sync time per user so status() returns a real value.
        redis.opsForValue().set(SYNC_TS_PREFIX + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofDays(90));
    }

    public SyncDto.StatusResponse status(String userId) {
        String ts = redis.opsForValue().get(SYNC_TS_PREFIX + userId);
        SyncDto.StatusResponse resp = new SyncDto.StatusResponse();
        resp.setLastSyncAt(ts != null ? Long.parseLong(ts) : 0L);
        resp.setPendingCount(0);
        resp.setStatus("OK");
        return resp;
    }
}
