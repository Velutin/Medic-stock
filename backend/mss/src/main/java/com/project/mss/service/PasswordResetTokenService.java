package com.project.mss.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.exception.InvalidTokenException;
import com.project.mss.model.entity.PasswordResetToken;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.TokenPurpose;
import com.project.mss.repository.PasswordResetTokenRepository;

/**
 * Single-use links sent by e-mail:
 *  - INVITATION (first access): valid for 48 hours;
 *  - PASSWORD_RESET ("forgot my password"): valid for 30 minutes.
 * Creating a new link invalidates any previous link of the user.
 */
@Service
public class PasswordResetTokenService {

    public static final Duration INVITATION_VALIDITY = Duration.ofHours(48);
    public static final Duration RESET_VALIDITY = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokenRepository;

    public PasswordResetTokenService(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public String createToken(User user, TokenPurpose purpose) {
        tokenRepository.deleteByUser(user);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setPurpose(purpose);
        token.setExpiryDate(LocalDateTime.now().plus(purpose == TokenPurpose.INVITATION ? INVITATION_VALIDITY : RESET_VALIDITY));
        token.setUsed(false);
        tokenRepository.save(token);
        return token.getToken();
    }

    /** Returns the valid, unused token; the caller marks it as used after changing the password. */
    @Transactional
    public PasswordResetToken validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));
        if (resetToken.getUsed()) {
            throw new InvalidTokenException("Token already used");
        }
        if (resetToken.isExpired()) {
            throw new InvalidTokenException("Token expired");
        }
        return resetToken;
    }

    @Transactional
    public void markTokenAsUsed(PasswordResetToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredTokens() {
        tokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
