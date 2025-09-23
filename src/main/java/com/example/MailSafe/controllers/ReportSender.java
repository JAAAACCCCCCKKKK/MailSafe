package com.example.MailSafe.controllers;

import com.example.MailSafe.MailSafeApplication;
import com.example.MailSafe.dto.ReportRequest;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.models.MailTaskStatus;
import com.example.MailSafe.service.MailTaskService;
import com.example.MailSafe.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportSender {
    private final MailTaskService mailTaskService;

    private final ReportService reportService;

    public ReportSender(MailTaskService mailTaskService,ReportService reportService) {
        this.mailTaskService = mailTaskService;
        this.reportService = reportService;
    }

    @PostMapping("/report")
    public void sendReport(@Valid @RequestBody ReportRequest rq) {
        if(rq==null||rq.getTaskId()== null||rq.getTaskId().toString().isEmpty()){
            MailSafeApplication.logger.error("Invalid request");
            return;
        }
        //get the task by id
        MailTask task = mailTaskService.getTaskById(rq.getTaskId());
        if(task ==null){
            MailSafeApplication.logger.error("Task not found: {}", rq.getTaskId());
            return;
        }
        if(!(task.getStatus()== MailTaskStatus.COMPLETED||task.getRisk()>=7)){
            MailSafeApplication.logger.error("Not eligible to report: {}", rq.getTaskId());
        }
        reportService.addSuspiciousSender(task.getSourceIp(), task.getSourceAddr());
    }
}
