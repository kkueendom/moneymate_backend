package com.moneymate.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsRepository smsRepository;

    public void deleteAll(String userId) {
        smsRepository.deleteAllByUserId(userId);
    }

    public SmsReportDto.ReportResult report(String userId, SmsReportDto.ReportRequest req) {
        int saved = 0;
        int skipped = 0;
        List<SmsReportDto.FailedEntry> failed = new ArrayList<>();

        // Batch dedup: one query for all incoming hashes instead of N individual queries
        List<String> incomingHashes = req.getEntries().stream()
                .map(SmsReportDto.SmsEntry::getSmsHash)
                .collect(Collectors.toList());
        Set<String> existingHashes = smsRepository
                .findHashesByUserIdAndSmsHashIn(userId, incomingHashes)
                .stream()
                .map(SmsDocument::getSmsHash)
                .collect(Collectors.toSet());

        for (SmsReportDto.SmsEntry e : req.getEntries()) {
            if (existingHashes.contains(e.getSmsHash())) {
                skipped++;
                continue;
            }
            try {
                smsRepository.save(SmsDocument.builder()
                        .userId(userId)
                        .smsHash(e.getSmsHash())
                        .sender(e.getSender())
                        .timestamp(e.getTimestamp())
                        .classifiedAs(e.getClassifiedAs())
                        .amount(e.getAmount())
                        .body(e.getBody())
                        .receivedAt(LocalDateTime.now())
                        .build());
                saved++;
            } catch (Exception ex) {
                log.warn("Failed to save SMS entry smsHash={}: {}", e.getSmsHash(), ex.getMessage());
                failed.add(new SmsReportDto.FailedEntry(e.getSmsHash(), ex.getMessage()));
            }
        }

        return new SmsReportDto.ReportResult(saved, skipped, failed);
    }
}
