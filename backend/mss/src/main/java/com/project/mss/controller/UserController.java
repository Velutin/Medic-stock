package com.project.mss.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.mss.config.SessionCookies;
import com.project.mss.dto.user.ChangePasswordDTO;
import com.project.mss.dto.user.UserDTO;
import com.project.mss.dto.user.UserFormDTO;
import com.project.mss.dto.user.UserHospitalsDTO;
import com.project.mss.dto.user.UserUpdateDTO;
import com.project.mss.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management (ADMIN) and the logged-in user's own data")
public class UserController {

    private final UserService userService;
    private final SessionCookies sessionCookies;

    public UserController(UserService userService, SessionCookies sessionCookies) {
        this.userService = userService;
        this.sessionCookies = sessionCookies;
    }

    // ------------------------------------------------------------ logged-in user

    @GetMapping("/me")
    @Operation(summary = "Logged-in user data")
    public UserDTO me() {
        return userService.me();
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change the logged-in user's password",
               description = "Requires the current password. Every other open session of the user is ended; "
                       + "this device stays logged in.")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto, HttpServletRequest request) {
        boolean rememberMe = sessionCookies.isRememberMe(request);
        String token = userService.changeOwnPassword(dto, rememberMe);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.create(token, rememberMe).toString())
                .build();
    }

    // ------------------------------------------------------------ administration

    @GetMapping
    @Operation(summary = "List users (ADMIN)")
    public Page<UserDTO> list(@ParameterObject @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return userService.list(pageable);
    }

    @PostMapping
    @Operation(summary = "Create a user and send the first-access invitation by e-mail (ADMIN)",
               description = "The user has no password until opening the e-mail link (valid for 48 hours).")
    public ResponseEntity<UserDTO> create(@RequestBody @Valid UserFormDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "User data (ADMIN)")
    public UserDTO find(@PathVariable Long id) {
        return userService.find(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change user data, profile or situation (ADMIN)",
               description = "Only the informed fields change. active=false blocks access immediately on every device.")
    public UserDTO update(@PathVariable Long id, @RequestBody @Valid UserUpdateDTO dto) {
        return userService.update(id, dto);
    }

    @PutMapping("/{id}/hospitals")
    @Operation(summary = "Set the hospitals the user works at (ADMIN)")
    public UserDTO setHospitals(@PathVariable Long id, @RequestBody @Valid UserHospitalsDTO dto) {
        return userService.setHospitals(id, dto);
    }

    @PostMapping("/{id}/invitation")
    @Operation(summary = "Resend the first-access invitation (ADMIN)",
               description = "Only for users who have not created a password yet. The previous link stops working.")
    public ResponseEntity<Void> resendInvitation(@PathVariable Long id) {
        userService.resendInvitation(id);
        return ResponseEntity.accepted().build();
    }
}
