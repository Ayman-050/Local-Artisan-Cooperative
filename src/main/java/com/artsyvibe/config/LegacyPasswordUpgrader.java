package com.artsyvibe.config;

import com.artsyvibe.model.User;
import com.artsyvibe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time migration: older rows (seed data, early sign-ups) stored passwords in plain text, and login
 * used to fall back to a plain string comparison for them. Login now only accepts BCrypt, so on startup
 * any remaining plain-text password is replaced by its BCrypt hash. Users keep the same password.
 * Idempotent: once every row is hashed this does nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPasswordUpgrader implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> legacy = userRepository.findWithLegacyPasswords();
        if (legacy.isEmpty()) {
            return;
        }
        for (User user : legacy) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.saveAll(legacy);
        log.warn("Hashed {} legacy plain-text password(s) with BCrypt.", legacy.size());
    }
}
