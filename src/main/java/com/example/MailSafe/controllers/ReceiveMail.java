package com.example.MailSafe.controllers;

import com.example.MailSafe.dto.*;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.service.MailTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
public class ReceiveMail {
    private final MailTaskService taskService;

    public ReceiveMail(MailTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody SubmitRequest request) {
        if (request.getRawEmailBase64() == null || request.getRawEmailBase64().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SubmitResponse(null, null, null));
        }
        MailTask task = taskService.createPendingTask(
                request.getRawEmailBase64(),
                request.getMessageId(),
                request.getSourceAddr(),
                request.getSourceIp()
        );

        //处理附件
        ArrayList<String> attachmentFileNames = new ArrayList<>();
        if (request.getAttachments()!=null&&!request.getAttachments().isEmpty()){
            for(AttachmentDto attachmentDto:request.getAttachments()){
                Attachment att = taskService.appendAttachmentToTask(task,attachmentDto);
                attachmentFileNames.add(att.getFileName());
            }
        }
        // 同步返回任务序列号（UUID）与初始状态（PENDING=待处理）
        SubmitResponse response = new SubmitResponse(
                task.getId(),
                task.getStatus(),
                attachmentFileNames.toArray(String[]::new)
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/submit")
    public ResponseEntity<AnalysisResponse> processSubmission(@Valid @RequestBody ProcessRequest req) {
        if (req.getTaskId() == null) {
            AnalysisResponse response = new AnalysisResponse(null, null, "Please specify request ID, which was issued after task submission", null, 0);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        MailTask task = taskService.getTaskById(req.getTaskId());
        if (task == null) {
            AnalysisResponse response = new AnalysisResponse(null, null, "Task not found", null, 0);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(null);
    }
}
