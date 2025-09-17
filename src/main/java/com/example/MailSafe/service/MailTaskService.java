package com.example.MailSafe.service;

import com.example.MailSafe.dto.AttachmentDto;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.models.MailTaskStatus;
import com.example.MailSafe.repo.AttachmentRepository;
import com.example.MailSafe.repo.MailTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class MailTaskService {
    private final MailTaskRepository mailTaskRepository;
    private final AttachmentRepository attachmentRepository;

    public MailTaskService(MailTaskRepository mailTaskRepository, AttachmentRepository attachmentRepository) {
        this.mailTaskRepository = mailTaskRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    public MailTask createPendingTask(String rawEmailBase64, String messageId, String sourceAddr, String sourceIp) {
        // 解码 Base64 → 存储 MIME 原文
        String rawEmail = new String(Base64.getDecoder().decode(rawEmailBase64), StandardCharsets.UTF_8);

        MailTask task = new MailTask();
        task.setId(UUID.randomUUID());
        task.setStatus(MailTaskStatus.PENDING);
        task.setRawEmail(rawEmail);
        task.setMessageId(messageId);
        task.setSourceIp(sourceIp);

        OffsetDateTime now = OffsetDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        return mailTaskRepository.save(task);
    }

    @Transactional
    public Attachment appendAttachmentToTask(MailTask task, AttachmentDto attachmentDto) {
        Attachment attachment=new Attachment();
        attachment.setFileName(attachmentDto.getFileName());
        attachment.setContentType(attachmentDto.getContentType());
        attachment.setData(attachmentDto.getData());
        attachment.setMailTask(task);
        return attachmentRepository.save(attachment);
    }

    @Transactional
    public MailTask getTaskById(UUID taskId) {
        return mailTaskRepository.findById(taskId).orElse(null);
    }
}
