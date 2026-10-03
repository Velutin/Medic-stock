package com.project.mss.config;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.project.mss.model.entity.User;
import com.project.mss.repository.UserRepository;
import com.project.mss.service.TokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates each request from the session cookie (or a Bearer token) and:
 *  - rejects inactive users, users without password and sessions issued before the last password change;
 *  - renews regular sessions on use (30 minutes of inactivity), so active users are not logged out.
 */
@Component
public class SecurityFilter extends OncePerRequestFilter {

    /** A regular session is renewed when less than this remains (avoids a new cookie on every request). */
    private static final Duration RENEW_WHEN_REMAINING_BELOW = Duration.ofMinutes(25);

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final SessionCookies sessionCookies;

    public SecurityFilter(TokenService tokenService, UserRepository userRepository, SessionCookies sessionCookies) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.sessionCookies = sessionCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recoverToken(request);
        if (token != null) {
            tokenService.validate(token).ifPresent(session -> userRepository.findById(session.userId())
                    .filter(User::isEnabled)
                    .filter(user -> issuedAfterLastPasswordChange(user, session))
                    .ifPresent(user -> {
                        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        renewIfNeeded(user, session, response);
                    }));
        }
        filterChain.doFilter(request, response);
    }

    private boolean issuedAfterLastPasswordChange(User user, TokenService.SessionToken session) {
        if (user.getSessionsValidAfter() == null || session.issuedAt() == null) return true;
        Instant validAfter = user.getSessionsValidAfter().atZone(ZoneId.systemDefault()).toInstant();
        return !session.issuedAt().isBefore(validAfter);
    }

    private void renewIfNeeded(User user, TokenService.SessionToken session, HttpServletResponse response) {
        if (session.rememberMe()) return; // fixed period counted from the login
        Duration remaining = Duration.between(Instant.now(), session.expiresAt());
        if (remaining.compareTo(RENEW_WHEN_REMAINING_BELOW) < 0) {
            String renewed = tokenService.generateToken(user, false);
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookies.create(renewed, false).toString());
        }
    }

    private String recoverToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (SessionCookies.NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
