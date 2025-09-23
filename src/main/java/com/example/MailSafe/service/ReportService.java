package com.example.MailSafe.service;

import com.example.MailSafe.models.SuspiciousSender;
import com.example.MailSafe.repo.SuspiciousSenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class ReportService {
    private final SuspiciousSenderRepository suspiciousSenderRepository;
    public ReportService(SuspiciousSenderRepository suspiciousSenderRepository){
        this.suspiciousSenderRepository = suspiciousSenderRepository;
    }
    @Transactional
    public void addSuspiciousSender(String sourceIp, String sourceAddr){
        SuspiciousSender suspiciousSender = new SuspiciousSender();
        suspiciousSender.setSourceIp(sourceIp);
        suspiciousSender.setSourceAddr(sourceAddr);
        suspiciousSenderRepository.save(suspiciousSender);
    }

    @Transactional
    public HashSet<SuspiciousSender> getSuspiciousSender(String sourceIp, String sourceAddr){
        return new HashSet<>(suspiciousSenderRepository.findAll().stream()
                .filter(s -> s.getSourceIp().equals(sourceIp) || s.getSourceAddr().equals(sourceAddr))
                .toList());
    }
}
