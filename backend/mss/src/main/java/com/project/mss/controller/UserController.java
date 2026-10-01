package com.project.mss.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.mss.dto.user.ChangePasswordDTO;
import com.project.mss.dto.user.ChangeRoleDTO;
import com.project.mss.dto.user.ForgotPasswordDTO;
import com.project.mss.dto.user.ResetPasswordDTO;
import com.project.mss.dto.user.UserBasicInfoDTO;
import com.project.mss.dto.user.UserDataUpdateDTO;
import com.project.mss.dto.user.UserProfileDTO;
import com.project.mss.dto.user.UserResponseDTO;
import com.project.mss.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
@Tag(name = "Users",description = "User management endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user profile", description = "Returns the authenticated user data.")
    public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
        String username = authentication.getName();
        UserProfileDTO profile = userService.getProfile(username);
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/add-role")
    @Operation(summary = "Add role to user", description = "Adds a role to an existing user. Requires ADMIN or MASTER.")
    public ResponseEntity<String> addRole(@RequestBody @Valid ChangeRoleDTO dto) {
        userService.addRole(dto);
        return ResponseEntity.ok("Role added successfully");
    }

    @PatchMapping("/remove-role")
    @Operation(summary = "Remove role from user", description = "Removes a role from an existing user. Requires ADMIN or MASTER.")
    public ResponseEntity<String> removeRole(@RequestBody @Valid ChangeRoleDTO dto) {
        userService.removeRole(dto);
        return ResponseEntity.ok("Role removed successfully");
    }

    @GetMapping("/all")
    @Operation(summary = "List all users", description = "Returns a paginated list of all users. Requires ADMIN or MASTER.")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(@ParameterObject Pageable pageable) {
        Page<UserResponseDTO> users = userService.getAllUsers(pageable);
        if (users.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get user data by username", description =  "Returns user data for a username. Requires ADMIN or MASTER.")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable String username) {
        UserResponseDTO user = userService.getUser(username);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/deactivate")
    @Operation(summary = "Deactivate user", description = "Deactivates an existing user. Requires ADMIN or MASTER.")
    public ResponseEntity<String> deactivateUser(@RequestBody @Valid UserBasicInfoDTO userDTO) {
        userService.deactivateUser(userDTO);
        return ResponseEntity.ok("User deactivated successfully");
    }

    @PatchMapping("/activate")
    @Operation(summary = "Activate user", description = "Activates an existing user. Requires ADMIN or MASTER.")
    public ResponseEntity<String> activateUser(@RequestBody @Valid UserBasicInfoDTO userDTO) {
        userService.activateUser(userDTO);
        return ResponseEntity.ok("User activated successfully");
    }

    @PatchMapping("/update")
    @Operation(summary = "Update user data", description = "Updates an existing user. Requires ADMIN or MASTER.")
    public ResponseEntity<String> updateUser(@RequestBody @Valid UserDataUpdateDTO userDTO){
        userService.update(userDTO);
        return ResponseEntity.ok("User updated successfully");
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Change user password", description = "Changes the password of the logged-in user.")
    public ResponseEntity<String> changePassword(@RequestBody @Valid ChangePasswordDTO passwordDTO, Authentication auth){ // Authentication injected by Spring Security
        userService.changePassword(auth.getName(), passwordDTO);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password recovery", description = "Sends an email with a password reset link.")
    public ResponseEntity<String> forgotPassword(@RequestBody @Valid ForgotPasswordDTO dto) {
        userService.requestPasswordReset(dto);
        return ResponseEntity.ok("Recovery email sent successfully");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the password using the token received by email.")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid ResetPasswordDTO dto) {
        userService.resetPassword(dto);
        return ResponseEntity.ok("Password reset successfully");
    }
}