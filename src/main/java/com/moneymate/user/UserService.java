package com.moneymate.user;

import com.moneymate.common.BusinessException;
import com.moneymate.sms.SmsService;
import com.moneymate.sync.LoanSyncService;
import com.moneymate.sync.SyncService;
import com.moneymate.sync.UdharSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SyncService syncService;
    private final LoanSyncService loanSyncService;
    private final UdharSyncService udharSyncService;
    private final SmsService smsService;

    public UserDto.ProfileResponse getProfile(String userId) {
        UserEntity user = findUser(userId);
        return toProfile(user);
    }

    @Transactional
    public UserDto.ProfileResponse updateProfile(String userId, UserDto.UpdateRequest req) {
        UserEntity user = findUser(userId);
        if (req.getName() != null) user.setName(req.getName());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        return toProfile(userRepository.save(user));
    }

    @Transactional
    public void deleteData(String userId) {
        // MySQL: cascade delete all sync data + soft-delete user in one transaction
        // If any step fails, the whole transaction rolls back — user remains active
        syncService.deleteAll(userId);      // also clears Redis sync timestamp
        loanSyncService.deleteAll(userId);
        udharSyncService.deleteAll(userId);

        UserEntity user = findUser(userId);
        user.setDeleted(true);
        user.setName(null);
        user.setAvatarUrl(null);
        userRepository.save(user);

        // MongoDB is a separate data source — cannot join the MySQL transaction.
        // Best-effort: log and alert on failure; ops can re-run manually if needed.
        try {
            smsService.deleteAll(userId);
        } catch (Exception e) {
            log.error("Failed to delete SMS records for userId={} — manual cleanup required", userId, e);
        }
    }

    private UserEntity findUser(String userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("User not found"));
    }

    private UserDto.ProfileResponse toProfile(UserEntity u) {
        return new UserDto.ProfileResponse(
                u.getId(), u.getPhone(), u.getName(), u.getAvatarUrl(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
    }
}
