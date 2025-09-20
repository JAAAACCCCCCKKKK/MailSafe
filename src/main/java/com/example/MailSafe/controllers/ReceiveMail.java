package com.example.MailSafe.controllers;

import com.example.MailSafe.MailSafeApplication;
import com.example.MailSafe.dto.*;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.models.MailTaskStatus;
import com.example.MailSafe.service.EmailAnalysisService;
import com.example.MailSafe.service.MailTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public class ReceiveMail {
    private final MailTaskService taskService;
    private final EmailAnalysisService analysisService;

    public ReceiveMail(MailTaskService taskService, EmailAnalysisService analysisService) {
        this.taskService = taskService;
        this.analysisService = analysisService;
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
    public ResponseEntity<SubmitResponse> processSubmission(@Valid @RequestBody ProcessRequest req) {
        if (req.getTaskId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        MailTask task = taskService.getTaskById(req.getTaskId());
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        task.setStatus(MailTaskStatus.PROCESSING);
        taskService.saveTask(task);
        CompletableFuture.runAsync(() -> {
            try {
                // 调用 EmailAnalysisService 进行分析
                int riskScore = analysisService.analyzeEmail(task, req.isUseAI());

                // 更新任务状态
                if (riskScore<0){
                    task.setStatus(MailTaskStatus.FAILED);
                }
                else{
                    task.setRisk(riskScore);
                    task.setStatus(MailTaskStatus.COMPLETED);
                }
            } catch (Exception e) {
                // 处理分析过程中的异常
                task.setStatus(MailTaskStatus.FAILED);
                MailSafeApplication.logger.error("Error processing email: {}", e.getMessage());
            }
            finally {
                taskService.saveTask(task);
            }
        });
        List<Attachment> attachments = task.getAttachments();
        ArrayList<String> attachmentNames = new ArrayList<>();
        for (Attachment att : attachments) {
            attachmentNames.add(att.getFileName());
        }
        if (task.getStatus()!=MailTaskStatus.PROCESSING){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        SubmitResponse response = new SubmitResponse(task.getId(), task.getStatus(), attachmentNames.toArray(String[]::new));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/resilt/{taskId}")
    public ResponseEntity<AnalysisResponse> getResult(@PathVariable UUID taskId) {
        if(taskId==null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        MailTask task = taskService.getTaskById(taskId);
        if(task==null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        if(task.getStatus()==MailTaskStatus.PENDING){
            MailSafeApplication.logger.error("Task not started yet: {}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        else if(task.getStatus()==MailTaskStatus.PROCESSING){
            MailSafeApplication.logger.info("Task still processing: {}", taskId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        else if(task.getStatus()==MailTaskStatus.FAILED){
            MailSafeApplication.logger.info("Task failed: {}", taskId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        else{
            AnalysisResponse ana = new AnalysisResponse(task.getMessageId(), task.getSourceIp(), task.getSourceAddr(), task.getAttachments().stream().map(Attachment::getFileName).toArray(String[]::new), task.getRisk());
            return ResponseEntity.status(HttpStatus.OK).body(ana);
        }
    }
}
