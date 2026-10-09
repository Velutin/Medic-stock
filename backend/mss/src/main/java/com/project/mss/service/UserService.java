package com.project.mss.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.user.ChangePasswordDTO;
import com.project.mss.dto.user.LoginDTO;
import com.project.mss.dto.user.PasswordResetDTO;
import com.project.mss.dto.user.PasswordResetRequestDTO;
import com.project.mss.dto.user.UserDTO;
import com.project.mss.dto.user.UserFormDTO;
import com.project.mss.dto.user.UserHospitalsDTO;
import com.project.mss.dto.user.UserUpdateDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.PasswordResetToken;
import com.project.mss.model.entity.Role;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.TokenPurpose;
import com.project.mss.model.enums.UserRole;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.RoleRepository;
import com.project.mss.repository.UserRepository;
import com.project.mss.util.BrazilianDocuments;

/**
 * Users log in by e-mail. A new user has no password: an invitation e-mail (valid for 48 hours)
 * lets the user create it. CPF (valid and unique) and mobile phone are required on every
 * registration and edit. The profile (role) is one of ADMIN, SURGICAL_TECH or USER; every user
 * also keeps the base USER role. MASTER cannot be assigned or changed through the API.
 */
@Service
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HospitalRepository hospitalRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final AccessControlService accessControlService;
    private final LoginThrottleService loginThrottleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, HospitalRepository hospitalRepository,
                       TokenService tokenService, EmailService emailService,
                       PasswordResetTokenService passwordResetTokenService,
                       AccessControlService accessControlService,
                       LoginThrottleService loginThrottleService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.hospitalRepository = hospitalRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.accessControlService = accessControlService;
        this.loginThrottleService = loginThrottleService;
    }

    // ============================================================ session

    /**
     * Signs in. {@code address} is the client address, used by the brake on repeated attempts;
     * null disables that part (a call outside the HTTP path).
     *
     * <p>The brake is checked <b>before</b> the password is verified, on purpose: verifying costs a
     * quarter of a second of processor (BCrypt), and that cost is exactly what an attacker would
     * spend to take the server down without guessing anything.
     */
    public String login(LoginDTO login, String address) {
        String email = normalizeEmail(login.email());
        loginThrottleService.checkAllowed(email, address);
        try {
            var credentials = new UsernamePasswordAuthenticationToken(email, login.password());
            var auth = authenticationManager.authenticate(credentials);
            loginThrottleService.recordSuccess(email);
            return tokenService.generateToken((User) auth.getPrincipal(), login.rememberMe());
        } catch (AuthenticationException e) {
            loginThrottleService.recordFailure(email, address);
            throw e;
        }
    }

    /** User of a freshly issued token (used in the login response, before the cookie is sent back). */
    @Transactional(readOnly = true)
    public UserDTO findByToken(String token) {
        Long id = tokenService.validate(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid session")).userId();
        return UserDTO.of(load(id));
    }

    @Transactional(readOnly = true)
    public UserDTO me() {
        return UserDTO.of(accessControlService.currentUser());
    }

    /**
     * Changes the current user's password and ends every other open session.
     * Returns the new token for the current session, so the user stays logged in on this device.
     */
    @Transactional
    public String changeOwnPassword(ChangePasswordDTO dto, boolean rememberMe) {
        User user = accessControlService.currentUser();
        if (user.getPassword() == null || !passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("The new password cannot be the same as the current one");
        }
        setPassword(user, dto.newPassword());
        emailService.sendPasswordChangeConfirmationEmail(user.getId(), user.getEmail());
        return tokenService.generateToken(user, rememberMe);
    }

    // ============================================================ first access and password reset

    /**
     * Sends a reset link. The response is the same whether the e-mail exists or not, so the endpoint
     * cannot be used to discover registered e-mails. A user who has not completed the first access
     * receives a new invitation instead.
     */
    @Transactional
    public void requestPasswordReset(PasswordResetRequestDTO dto) {
        userRepository.findByEmail(normalizeEmail(dto.email()))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .ifPresent(user -> {
                    if (user.isPendingFirstAccess()) {
                        sendInvitation(user);
                    } else {
                        String token = passwordResetTokenService.createToken(user, TokenPurpose.PASSWORD_RESET);
                        emailService.sendPasswordResetEmail(user.getId(), user.getEmail(), user.getName(), token);
                    }
                });
    }

    /** Creates the password from an e-mail link: first access (invitation) or password reset. */
    @Transactional
    public void resetPassword(PasswordResetDTO dto) {
        PasswordResetToken token = passwordResetTokenService.validateToken(dto.token());
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("This user is inactive");
        }
        if (user.getPassword() != null && passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new BusinessRuleException("The new password cannot be the same as the current one");
        }
        setPassword(user, dto.newPassword());
        passwordResetTokenService.markTokenAsUsed(token);
    }

    // ============================================================ administration

    @Transactional(readOnly = true)
    public Page<UserDTO> list(Pageable pageable) {
        accessControlService.requireManager();
        return userRepository.findAll(pageable).map(UserDTO::of);
    }

    @Transactional(readOnly = true)
    public UserDTO find(Long id) {
        accessControlService.requireManager();
        return UserDTO.of(load(id));
    }

    /** Creates the user without password and sends the first-access invitation. */
    @Transactional
    public UserDTO create(UserFormDTO dto) {
        accessControlService.requireManager();
        String email = normalizeEmail(dto.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email " + email + " is already registered");
        }
        String cpf = validCpf(dto.cpf());
        if (userRepository.existsByCpf(cpf)) {
            throw new BusinessRuleException("CPF " + dto.cpf() + " is already registered");
        }

        User user = new User(dto.name().trim(), email, role(UserRole.USER));
        user.setCpf(cpf);
        user.setPhone(validPhone(dto.phone()));
        applyRole(user, dto.role());
        if (dto.hospitalIds() != null) {
            user.getHospitals().addAll(hospitals(dto.hospitalIds()));
        }
        user = userRepository.save(user);
        sendInvitation(user);
        return UserDTO.of(user);
    }

    /** Changes only the informed fields. CPF and phone must be valid (and filled) after the change. */
    @Transactional
    public UserDTO update(Long id, UserUpdateDTO dto) {
        accessControlService.requireManager();
        User user = load(id);

        if (dto.name() != null && !dto.name().isBlank()) {
            user.setName(dto.name().trim());
        }
        if (dto.email() != null && !dto.email().isBlank()) {
            String email = normalizeEmail(dto.email());
            if (userRepository.existsByEmailAndIdNot(email, id)) {
                throw new BusinessRuleException("Email " + email + " is already registered");
            }
            user.setEmail(email);
        }
        if (dto.cpf() != null && !dto.cpf().isBlank()) {
            String cpf = validCpf(dto.cpf());
            if (userRepository.existsByCpfAndIdNot(cpf, id)) {
                throw new BusinessRuleException("CPF " + dto.cpf() + " is already registered");
            }
            user.setCpf(cpf);
        }
        if (dto.phone() != null && !dto.phone().isBlank()) {
            user.setPhone(validPhone(dto.phone()));
        }
        if (user.getCpf() == null || user.getPhone() == null) {
            throw new BusinessRuleException("CPF and mobile phone are required");
        }
        if (dto.role() != null) {
            if (user.hasAnyRole(UserRole.MASTER.name())) {
                throw new BusinessRuleException("The MASTER profile cannot be changed");
            }
            applyRole(user, dto.role());
        }
        if (dto.active() != null) {
            setActive(user, dto.active());
        }
        return UserDTO.of(userRepository.save(user));
    }

    @Transactional
    public UserDTO setHospitals(Long id, UserHospitalsDTO dto) {
        accessControlService.requireManager();
        User user = load(id);
        user.getHospitals().clear();
        user.getHospitals().addAll(hospitals(dto.hospitalIds()));
        return UserDTO.of(userRepository.save(user));
    }

    /** Sends a new first-access link (the previous one stops working). */
    @Transactional
    public void resendInvitation(Long id) {
        accessControlService.requireManager();
        User user = load(id);
        if (!user.isPendingFirstAccess()) {
            throw new BusinessRuleException("This user has already completed the first access");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("This user is inactive");
        }
        sendInvitation(user);
    }

    // ============================================================ helpers

    private void sendInvitation(User user) {
        String token = passwordResetTokenService.createToken(user, TokenPurpose.INVITATION);
        emailService.sendInvitationEmail(user.getId(), user.getEmail(), user.getName(), token);
    }

    /** Sets the password and ends every session issued before now. */
    private void setPassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setSessionsValidAfter(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        userRepository.save(user);
    }

    /** Keeps the base USER role and sets the profile role (ADMIN, SURGICAL_TECH or USER only). */
    private void applyRole(User user, UserRole profile) {
        if (profile == UserRole.MASTER) {
            throw new BusinessRuleException("The MASTER profile cannot be assigned");
        }
        user.getRoles().removeIf(r -> r.getRole().equals(UserRole.ADMIN.name())
                || r.getRole().equals(UserRole.SURGICAL_TECH.name()));
        if (user.getRoles().stream().noneMatch(r -> r.getRole().equals(UserRole.USER.name()))) {
            user.addRole(role(UserRole.USER));
        }
        if (profile != UserRole.USER) {
            user.addRole(role(profile));
        }
    }

    private void setActive(User user, boolean active) {
        if (!active && user.hasAnyRole(UserRole.MASTER.name())) {
            throw new BusinessRuleException("The MASTER user cannot be deactivated");
        }
        if (!active && user.getId().equals(accessControlService.currentUser().getId())) {
            throw new BusinessRuleException("You cannot deactivate your own user");
        }
        user.setIsActive(active);
        user.setDeactivatedAt(active ? null : LocalDateTime.now());
    }

    private Role role(UserRole r) {
        return roleRepository.findByRole(r.name())
                .orElseThrow(() -> new EntityNotFoundException("Role " + r + " not found"));
    }

    private Set<Hospital> hospitals(Set<Long> ids) {
        var found = new HashSet<>(hospitalRepository.findAllById(ids));
        if (found.size() != ids.size()) {
            throw new EntityNotFoundException("One or more hospitals do not exist");
        }
        return found;
    }

    private User load(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User " + id + " not found"));
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String validCpf(String cpf) {
        if (!BrazilianDocuments.isValidCpf(cpf)) {
            throw new BusinessRuleException("Invalid CPF");
        }
        return BrazilianDocuments.digits(cpf);
    }

    private static String validPhone(String phone) {
        if (!BrazilianDocuments.isValidMobile(phone)) {
            throw new BusinessRuleException("Invalid mobile phone: use DDD + 9 digits, e.g. (71) 99999-9999");
        }
        return BrazilianDocuments.digits(phone);
    }
}
