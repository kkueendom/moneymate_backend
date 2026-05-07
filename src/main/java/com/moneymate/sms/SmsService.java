package com.moneymate.sms;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsRepository smsRepository;

    public void deleteAll(String userId) {
        smsRepository.deleteAllByUserId(userId);
    }

    public int report(String userId, SmsReportDto.ReportRequest req) {
        List<SmsDocument> toSave = req.getEntries().stream()
                .filter(e -> !smsRepository.existsBySmsHash(e.getSmsHash()))
                .map(e -> SmsDocument.builder()
                        .userId(userId)
                        .smsHash(e.getSmsHash())
                        .sender(e.getSender())
                        .timestamp(e.getTimestamp())
                        .classifiedAs(e.getClassifiedAs())
                        .amount(e.getAmount())
                        .body(e.getBody())
                        .receivedAt(LocalDateTime.now())
                        .build())
                .toList();
        smsRepository.saveAll(toSave);
        return toSave.size();
    }
}
