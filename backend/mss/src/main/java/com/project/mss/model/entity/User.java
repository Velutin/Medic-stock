package com.project.mss.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login and unique identifier (always stored in lower case). */
    @Column(nullable = false, unique = true)
    private String email;

    /** Full name; not unique. */
    @Column(nullable = false)
    private String name;

    /** CPF digits (11), unique. */
    @Column(unique = true, length = 11)
    private String cpf;

    /** Mobile phone digits: DDD + 9 digits. */
    @Column(length = 11)
    private String phone;

    /** Null until the user opens the first-access link and creates a password. */
    private String password;

    /** Sessions issued before this instant are rejected (set on password change). */
    @Column(name = "sessions_valid_after")
    private LocalDateTime sessionsValidAfter;

    private Boolean isActive = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private LocalDateTime deactivatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private List<Role> roles = new ArrayList<Role>();

    /** Hospitals the user works at (surgical techs only see these). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_hospital",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "hospital_id"))
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private java.util.Set<Hospital> hospitals = new java.util.HashSet<>();

    public User(String name, String email, Role role) {
        this.name = name;
        this.email = email;
        roles.add(role);
    }

    /** Invited users cannot log in until they create a password through the first-access link. */
    public boolean isPendingFirstAccess() {
        return password == null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (this.email != null) {
            this.email = this.email.toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (this.email != null) {
            this.email = this.email.toLowerCase();
        }
    }

    /** Spring Security login identifier: the e-mail. Use getName() for the person's name. */
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void addRole(Role newRole) {
        roles.add(newRole);
    }


    public boolean hasAnyRole(String... names) {
        for (Role r : roles) {
            for (String n : names) {
                if (n.equals(r.getRole())) return true;
            }
        }
        return false;
    }

    /** ADMIN and MASTER see every hospital. */
    public boolean isManager() {
        return hasAnyRole("ADMIN", "MASTER");
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive && !isPendingFirstAccess();
    }
}
