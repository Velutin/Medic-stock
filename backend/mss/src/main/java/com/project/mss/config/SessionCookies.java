package com.project.mss.config;

import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.project.mss.service.TokenService;

/**
 * Builds the session cookie.
 *  - "Keep me signed in": persistent cookie, kept for the remember period even after closing the browser.
 *  - Otherwise: session cookie, discarded when the browser is closed.
 */
@Component
public class SessionCookies {

    public static final String NAME = "accessToken";

    private final TokenService tokenService;
    private final boolean secure;

    public SessionCookies(TokenService tokenService, @Value("${app.security.cookie-secure:false}") boolean secure) {
        this.tokenService = tokenService;
        this.secure = secure;
    }

    public ResponseCookie create(String token, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = base(token);
        if (rememberMe) {
            builder.maxAge(tokenService.rememberDuration());
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /** Whether the session of this request was opened with "keep me signed in". */
    public boolean isRememberMe(HttpServletRequest request) {
        if (request.getCookies() == null) return false;
        for (Cookie cookie : request.getCookies()) {
            if (NAME.equals(cookie.getName())) {
                return tokenService.validate(cookie.getValue()).map(TokenService.SessionToken::rememberMe).orElse(false);
            }
        }
        return false;
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict");
    }
}
