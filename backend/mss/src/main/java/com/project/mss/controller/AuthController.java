package com.project.mss.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.config.SessionCookies;
import com.project.mss.dto.user.LoginDTO;
import com.project.mss.dto.user.PasswordResetDTO;
import com.project.mss.dto.user.PasswordResetRequestDTO;
import com.project.mss.dto.user.UserDTO;
import com.project.mss.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Session (login/logout), first access and password reset")
public class AuthController {

    private final UserService userService;
    private final SessionCookies sessionCookies;

    public AuthController(UserService userService, SessionCookies sessionCookies) {
        this.userService = userService;
        this.sessionCookies = sessionCookies;
    }

    @PostMapping("/session")
    @Operation(summary = "Log in with e-mail and password",
               description = "rememberMe=true keeps the session on this device for 7 days; otherwise it ends "
                       + "after 30 minutes without use or when the browser is closed.")
    public ResponseEntity<UserDTO> login(@RequestBody @Valid LoginDTO login, HttpServletRequest request) {
        // The real client address: nginx sends X-Forwarded-For and server.forward-headers-strategy
        // makes Spring honour it, so this is not the address of the proxy.
        String token = userService.login(login, request.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.create(token, login.rememberMe()).toString())
                .body(userService.findByToken(token));
    }

    @GetMapping("/session")
    @Operation(summary = "Current session user")
    public UserDTO session() {
        return userService.me();
    }

    @DeleteMapping("/session")
    @Operation(summary = "Log out (clears the session cookie)")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString())
                .build();
    }

    @PostMapping("/password-reset-requests")
    @Operation(summary = "Request a password reset link by e-mail",
               description = "Always returns success, whether the e-mail is registered or not. "
                       + "Users who have not completed the first access receive a new invitation.")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody @Valid PasswordResetRequestDTO dto) {
        userService.requestPasswordReset(dto);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets")
    @Operation(summary = "Create the password from an e-mail link (first access or password reset)")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetDTO dto) {
        userService.resetPassword(dto);
        return ResponseEntity.noContent().build();
    }
}
