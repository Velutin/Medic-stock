package com.project.mss.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.project.mss.model.entity.User;

/**
 * Session tokens (JWT in an HttpOnly cookie).
 *  - Regular session: expires after {@code idleMinutes} without use; renewed on every request (sliding).
 *  - "Keep me signed in": valid for {@code rememberDays} counted from the login, without renewal.
 * The subject is the user id, so changing the e-mail does not break open sessions.
 */
@Service
public class TokenService {

    private static final String ISSUER = "MSS API";
    private static final String REMEMBER_CLAIM = "rem";

    public record SessionToken(Long userId, boolean rememberMe, Instant issuedAt, Instant expiresAt) { }

    @Value("${api.security.token.secret}")
    private String secret;

    @Value("${app.security.session.idle-minutes:30}")
    private long idleMinutes;

    @Value("${app.security.session.remember-days:7}")
    private long rememberDays;

    public String generateToken(User user, boolean rememberMe) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = rememberMe ? now.plus(Duration.ofDays(rememberDays)) : now.plus(idleTimeout());
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(String.valueOf(user.getId()))
                    .withClaim(REMEMBER_CLAIM, rememberMe)
                    .withIssuedAt(now)
                    .withExpiresAt(expiresAt)
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException e) {
            throw new IllegalStateException("Error generating JWT token", e);
        }
    }

    /** Returns the session data when the token is valid and not expired. */
    public Optional<SessionToken> validate(String token) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).withIssuer(ISSUER).build().verify(token);
            Boolean remember = jwt.getClaim(REMEMBER_CLAIM).asBoolean();
            return Optional.of(new SessionToken(Long.valueOf(jwt.getSubject()), remember != null && remember,
                    jwt.getIssuedAtAsInstant(), jwt.getExpiresAtAsInstant()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Duration idleTimeout() {
        return Duration.ofMinutes(idleMinutes);
    }

    public Duration rememberDuration() {
        return Duration.ofDays(rememberDays);
    }
}
