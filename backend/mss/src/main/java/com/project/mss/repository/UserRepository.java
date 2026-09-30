package com.project.mss.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import com.project.mss.model.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    UserDetails findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
