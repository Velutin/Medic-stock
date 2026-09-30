package com.project.mss.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.user.ChangePasswordDTO;
import com.project.mss.dto.user.ChangeRoleDTO;
import com.project.mss.dto.user.ForgotPasswordDTO;
import com.project.mss.dto.user.LoginDTO;
import com.project.mss.dto.user.ResetPasswordDTO;
import com.project.mss.dto.user.UserBasicInfoDTO;
import com.project.mss.dto.user.UserDataUpdateDTO;
import com.project.mss.dto.user.UserFromEntityDTO;
import com.project.mss.dto.user.UserProfileDTO;
import com.project.mss.dto.user.UserRegDTO;
import com.project.mss.dto.user.UserResponseDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.exception.InvalidOperationException;
import com.project.mss.model.entity.Role;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.UserRole;
import com.project.mss.repository.RoleRepository;
import com.project.mss.repository.UserRepository;

@Service
public class UserService {

    private final AuthenticationManager authenticationManager;    
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, TokenService tokenService, EmailService emailService,                       
                       PasswordResetTokenService passwordResetTokenService) {
        this.authenticationManager = authenticationManager;        
        
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public String login(LoginDTO login) {
        var usernamePassToken = new UsernamePasswordAuthenticationToken(login.username(), login.password());
        var auth = authenticationManager.authenticate(usernamePassToken);
        return tokenService.generateToken((User) auth.getPrincipal());
    }

    public UserProfileDTO getProfile(String username) {
        User user = findUserByUsername(username);        

        

        return new UserProfileDTO(
                user.getId(),                
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream().map(r -> r.getAuthority()).toList()
        );
    }

    public void register(UserRegDTO newUser) {
        validateUniqueUsername(newUser.username());
        validateUniqueEmail(newUser.email());
        String encodedPassword = new BCryptPasswordEncoder().encode(newUser.password());
        Role defaultRole = roleRepository.findByRole(UserRole.USER.name())
                .orElseThrow(() -> new EntityNotFoundException("Role USER not found"));
        User user = new User(newUser.username(), newUser.email(), encodedPassword, defaultRole);
        userRepository.save(user);
        emailService.sendUserRegistrationEmail(user.getId(),user.getEmail());
    }

    public void addRole(ChangeRoleDTO dto) {
        Role newRole = roleRepository.findByRole(dto.role())
                .orElseThrow(() -> new EntityNotFoundException("Role " + dto.role() + " not found"));
        User user = findUserByUsername(dto.username());

        if (user.getRoles().contains(newRole)) {
            throw new BusinessRuleException("User already has role " + dto.role());
        }

        user.addRole(newRole);
        userRepository.save(user);
    }

    public void removeRole(ChangeRoleDTO dto) {
        Role roleToRemove = roleRepository.findByRole(dto.role())
                .orElseThrow(() -> new EntityNotFoundException("Role " + dto.role() + " not found"));
        User user = findUserByUsername(dto.username());

        if (roleToRemove.getAuthority().equals(UserRole.MASTER.name())) {
            throw new BusinessRuleException("Cannot remove the MASTER role");
        }
        if (roleToRemove.getAuthority().equals(UserRole.USER.name())) {
            throw new BusinessRuleException("Cannot remove the default USER role");
        }
        if (!user.getRoles().contains(roleToRemove)) {
            throw new BusinessRuleException("User does not have role " + dto.role());
        }

        user.removeRole(roleToRemove);
        userRepository.save(user);
    }

    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return users.map(user -> new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getAuthority).toList()
        ));
    }

    public void deactivateUser(UserBasicInfoDTO userDTO) {
        User user = findUserByUsername(userDTO.username());

        if (!user.getIsActive()) {
            throw new InvalidOperationException("User " + userDTO.username() + " is already deactivated");
        }

        user.setIsActive(false);
        user.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void activateUser(UserBasicInfoDTO userDTO) {
        User user = findUserByUsername(userDTO.username());

        if (user.getIsActive()) {
            throw new InvalidOperationException("User " + userDTO.username() + " is already active");
        }

        user.setIsActive(true);
        user.setDeactivatedAt(null);
        userRepository.save(user);
    }

    public UserResponseDTO getUser(String username) {
        User user = findUserByUsername(username);
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getAuthority).toList()
        );
    }

    public void update(UserDataUpdateDTO dto){
        User user = findUserByUsername(dto.username());
        if (dto.email() != null && !dto.email().isBlank()) {
            validateUniqueEmail(dto.email());
            user.setEmail(dto.email());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            String encoded = new BCryptPasswordEncoder().encode(dto.password());
            user.setPassword(encoded);
        }
        if (dto.roles() != null && !dto.roles().isEmpty()) {
            var resolvedRoles = dto.roles().stream()
                    .map(r -> roleRepository.findByRole(r.getAuthority())
                            .orElseThrow(() -> new EntityNotFoundException("Role " + r.getAuthority() + " not found")))
                    .toList();
            user.getRoles().clear();
            user.getRoles().addAll(new java.util.HashSet<>(resolvedRoles));
        }
        userRepository.save(user);
    }

    public void changePassword(String username, ChangePasswordDTO passwordDTO) {
        User user = findUserByUsername(username);
        String encodedPassword = new BCryptPasswordEncoder().encode(passwordDTO.newPassword());
        user.setPassword(encodedPassword);
        userRepository.save(user);
        emailService.sendPasswordChangeConfirmationEmail(user.getId(), user.getEmail());
    }

    public User findOrCreateUser(UserFromEntityDTO userDTO) {
        if (userDTO.username() == null || userDTO.username().isBlank()) {
            String username = generateUsernameFromName(userDTO.name());
            validateUniqueEmail(userDTO.email());
            String tempPassword = new BCryptPasswordEncoder().encode("temp123");
            Role defaultRole = roleRepository.findByRole(UserRole.USER.name())
                    .orElseThrow(() -> new EntityNotFoundException("Role USER not found"));
            User newUser = new User(username, userDTO.email(), tempPassword, defaultRole);
            emailService.sendUserAutoRegistrationEmail(newUser.getId(),newUser.getEmail(), newUser.getUsername());
            return userRepository.save(newUser);
        }
        User user = (User) userRepository.findByUsername(userDTO.username());
        if (user == null) {
            throw new EntityNotFoundException("User " + userDTO.username() + " not found");
        }
        return user;
    }

    public List<String> getUserRoles(String username) {
        User user = findUserByUsername(username);
        return user.getRoles().stream()
            .map(Role::getRole)
            .collect(Collectors.toList());
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordDTO dto) {
        User user = userRepository.findByEmail(dto.email())
            .orElseThrow(() -> new EntityNotFoundException("No user found with this email"));

        if (!user.getUsername().equals(dto.username())) {
            throw new BusinessRuleException("Username does not match the given email");
        }

    String token = passwordResetTokenService.createToken(user);
    emailService.sendPasswordResetEmail(user.getId(), user.getEmail(), user.getUsername(), token);
}

    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        User user = passwordResetTokenService.validateToken(dto.token());
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("The new password cannot be the same as the current one");
        }
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
        passwordResetTokenService.markTokenAsUsed(dto.token());
    }

    // Helper methods
    private String generateUsernameFromName(String name) {
        String baseName = (name == null || name.isBlank()) ? "user" : name;
        // Remove acentos e caracteres especiais
        String normalized = java.text.Normalizer.normalize(baseName, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        // Converts to lowercase and removes spaces
        String username = normalized.toLowerCase()
                .replaceAll("\\s+", ".")
                .replaceAll("[^a-z0-9.]", "");
        // Limita o tamanho
        if (username.length() > 20) {
            username = username.substring(0, 20);
        }
        return ensureUniqueUsername(username);
    }

    private String ensureUniqueUsername(String baseUsername) {
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }
        return username;
    }
    User findUserByUsername(String username) {
        User user = (User) userRepository.findByUsername(username);
        if (user == null) {
            throw new EntityNotFoundException("User " + username + " not found");
        }
        return user;
    }
    public User findUserDTOByUsername(String username) {
        User user = (User) userRepository.findByUsername(username);
        
        if (user == null) {
            throw new EntityNotFoundException("User " + username + " not found");
        }
        return user;
    }

    private void validateUniqueUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException("Username " + username + " is already in use");
        }
    }

    private void validateUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email " + email + " is already registered");
        }
    }    

}