package com.example.MailSafe.dto;

import lombok.AllArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
public class ProcessRequest {
    private UUID taskId;
    private boolean useAI = false;
    public UUID getTaskId(){return taskId;}
    public void setTaskId(String taskId){this.taskId = UUID.fromString(taskId);}
    public boolean isUseAI(){return useAI;}
    public void setUseAI(boolean useAI){this.useAI = useAI;}
}
