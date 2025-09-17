// src/main/java/com/example/mail/repo/AttachmentRepository.java
package com.example.MailSafe.repo;

import com.example.MailSafe.models.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {}

