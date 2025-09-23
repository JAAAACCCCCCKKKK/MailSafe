package com.example.MailSafe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.UUID;

@AllArgsConstructor
public class ProcessRequest {
    @NotNull
    private UUID taskId;
    private boolean useAI;
    public UUID getTaskId(){return taskId;}
    public void setTaskId(String taskId){this.taskId = UUID.fromString(taskId);}
    public boolean isUseAI(){return useAI;}
    public void setUseAI(boolean useAI){this.useAI = useAI;}
}
