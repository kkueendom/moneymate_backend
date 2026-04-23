package com.moneymate.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByPhoneAndDeletedFalse(String phone);
    boolean existsByPhone(String phone);
    boolean existsByIdAndDeletedFalse(String id);
}
