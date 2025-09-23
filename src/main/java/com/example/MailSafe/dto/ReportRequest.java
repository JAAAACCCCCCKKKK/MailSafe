package com.example.MailSafe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ReportRequest {
    @NotNull
    private UUID taskId;
    public UUID getTaskId() {
        return taskId;
    }
    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }
}
