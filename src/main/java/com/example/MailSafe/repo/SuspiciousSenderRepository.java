// src/main/java/com/example/mail/repo/SuspiciousSenderRepository.java
package com.example.MailSafe.repo;

import com.example.MailSafe.models.SuspiciousSender;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspiciousSenderRepository extends JpaRepository<SuspiciousSender, UUID> {}
