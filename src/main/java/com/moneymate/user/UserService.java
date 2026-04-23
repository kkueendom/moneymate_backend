package com.moneymate.user;

import com.moneymate.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
        UserEntity user = findUser(userId);
        user.setDeleted(true);
        user.setName(null);
        user.setAvatarUrl(null);
        userRepository.save(user);
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
