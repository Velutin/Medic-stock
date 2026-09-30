package com.project.mss.service;

import com.project.mss.exception.InvalidTokenException;
import com.project.mss.model.entity.PasswordResetToken;
import com.project.mss.model.entity.User;
import com.project.mss.repository.PasswordResetTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetTokenService {

    private static final int EXPIRATION_MINUTES = 30;

    private final PasswordResetTokenRepository tokenRepository;

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public String createToken(User user) {
        tokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        resetToken.setUsed(false);

        tokenRepository.save(resetToken);
        return token;
    }

    @Transactional
    public User validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (resetToken.getUsed()) {
            throw new InvalidTokenException("Token already used");
        }

        if (resetToken.isExpired()) {
            throw new InvalidTokenException("Token expired");
        }

        return resetToken.getUser();
    }

    @Transactional
    public void markTokenAsUsed(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredTokens() {
        tokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}