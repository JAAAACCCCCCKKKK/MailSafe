package com.example.MailSafe.controllers;

import com.example.MailSafe.MailSafeApplication;
import com.example.MailSafe.dto.*;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.models.MailTaskStatus;
import com.example.MailSafe.service.EmailAnalysisService;
import com.example.MailSafe.service.MailTaskService;
import com.example.MailSafe.utils.EmailParser;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitResponse> submit(
            @RequestPart(value = "rawEmail", required = false) String rawEmail,
            @RequestPart(value = "emlFile", required = false) MultipartFile emlFile) {
        if ((rawEmail == null || rawEmail.isBlank()) && (emlFile == null || emlFile.isEmpty())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SubmitResponse(null, null, null));
        }

        byte[] rawEmailBytes;
        if (rawEmail != null && !rawEmail.isBlank()) {
            rawEmailBytes = rawEmail.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                rawEmailBytes = emlFile.getBytes();
            } catch (IOException e) {
                MailSafeApplication.logger.error("Failed to read uploaded email file", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SubmitResponse(null, null, null));
            }
        }
        return handleSubmission(rawEmailBytes);
    }

    @PostMapping(value = "/submit", consumes = {MediaType.TEXT_PLAIN_VALUE, "message/rfc822", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<SubmitResponse> submitRaw(@RequestBody byte[] rawEmailBytes) {
        if (rawEmailBytes == null || rawEmailBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SubmitResponse(null, null, null));
        }
        return handleSubmission(rawEmailBytes);
    }

    private ResponseEntity<SubmitResponse> handleSubmission(byte[] rawEmailBytes) {
        EmailParser.ParsedEmail parsedEmail;
        try {
            parsedEmail = EmailParser.parse(rawEmailBytes);
        } catch (MessagingException | IOException e) {
            MailSafeApplication.logger.error("Failed to parse email", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SubmitResponse(null, null, null));
        }

        MailTask task = taskService.createPendingTask(
                parsedEmail.rawEmail(),
                parsedEmail.messageId(),
                parsedEmail.sourceAddr(),
                parsedEmail.sourceIp()
        );

        ArrayList<String> attachmentFileNames = new ArrayList<>();
        for (AttachmentDto attachmentDto : parsedEmail.attachments()) {
            Attachment att = taskService.appendAttachmentToTask(task, attachmentDto);
            attachmentFileNames.add(att.getFileName());
        }

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

    @GetMapping("/result/{taskId}")
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
