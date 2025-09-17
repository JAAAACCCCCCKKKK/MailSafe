package com.example.MailSafe.dto;

import com.example.MailSafe.models.MailTaskStatus;

import java.util.UUID;

public class SubmitResponse {
    private UUID taskId;
    private MailTaskStatus status;
    private String[] attachments;

    public SubmitResponse(UUID taskId, MailTaskStatus status, String[] attachments) {
        this.taskId = taskId;
        this.status = status;
    }
    public UUID getTaskId() { return taskId; }
    public MailTaskStatus getStatus() { return status; }
    public String[] getAttachments() { return attachments; }
}
