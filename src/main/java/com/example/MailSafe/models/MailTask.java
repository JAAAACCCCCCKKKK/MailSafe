// src/main/java/com/example/mail/model/MailTask.java
package com.example.MailSafe.models;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "mail_tasks")
public class MailTask {
    // getters/setters
    @Getter
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MailTaskStatus status;

    @Lob
    @Column(name = "raw_email", nullable = false)
    private String rawEmail; // 明文

    @OneToMany(mappedBy = "mailTask", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // 可选元数据
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "source_addr")
    private String sourceAddr;

    @Column(name = "source_ip")
    private String sourceIp;

    public void setId(UUID id) { this.id = id; }
    public MailTaskStatus getStatus() { return status; }
    public void setStatus(MailTaskStatus status) { this.status = status; }
    public String getRawEmail() { return rawEmail; }
    public void setRawEmail(String rawEmail) { this.rawEmail = rawEmail; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    // 添加单个附件
    public void addAttachment(Attachment attachment) {
        if (this.attachments == null) {
            this.attachments = new ArrayList<>();
        }
        this.attachments.add(attachment);
        attachment.setMailTask(this); // 维护双向关系
    }

    // 移除附件
    public void removeAttachment(Attachment attachment) {
        if (this.attachments != null) {
            this.attachments.remove(attachment);
            attachment.setMailTask(null); // 解除关联
        }
    }

    public String getSourceAddr() {
        return sourceAddr;
    }

    public void setSourceAddr(String sourceAddr) {
        this.sourceAddr = sourceAddr;
    }

}

