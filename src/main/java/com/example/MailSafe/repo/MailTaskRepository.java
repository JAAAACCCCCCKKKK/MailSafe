// src/main/java/com/example/mail/repo/MailTaskRepository.java
package com.example.MailSafe.repo;

import com.example.MailSafe.models.MailTask;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailTaskRepository extends JpaRepository<MailTask, UUID> {}
