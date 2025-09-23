package com.example.MailSafe.service;

import com.example.MailSafe.dto.AttachmentDto;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.models.MailTaskStatus;
import com.example.MailSafe.repo.AttachmentRepository;
import com.example.MailSafe.repo.MailTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    public MailTask createPendingTask(String rawEmail, String messageId, String sourceAddr, String sourceIp) {
        MailTask task = new MailTask();
        task.setId(UUID.randomUUID());
        task.setStatus(MailTaskStatus.PENDING);
        task.setRawEmail(rawEmail);
        task.setMessageId(messageId);
        task.setSourceAddr(sourceAddr);
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
        task.addAttachment(attachment);
        task.setUpdatedAt(OffsetDateTime.now());
        mailTaskRepository.save(task);
        return attachmentRepository.save(attachment);
    }

    @Transactional
    public MailTask getTaskById(UUID taskId) {
        return mailTaskRepository.findById(taskId).orElse(null);
    }

    @Transactional
    public MailTask saveTask(MailTask task) {
        task.setUpdatedAt(OffsetDateTime.now());
        return mailTaskRepository.save(task);
    }
}
