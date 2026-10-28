package com.project.mss.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.mss.model.entity.IncomingEmail;
import com.project.mss.model.enums.IncomingEmailStatus;

public interface IncomingEmailRepository extends JpaRepository<IncomingEmail, Long> {

    Optional<IncomingEmail> findByMessageUid(String messageUid);

    Page<IncomingEmail> findAllByOrderByReadAtDescIdDesc(Pageable pageable);

    long countByStatus(IncomingEmailStatus status);
}
