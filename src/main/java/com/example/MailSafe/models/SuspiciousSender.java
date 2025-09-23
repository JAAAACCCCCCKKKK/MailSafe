package com.example.MailSafe.models;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "suspicious_senders")
public class SuspiciousSender {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name="source_ip",unique = true)
    private String sourceIp;

    @Column(name="source_addr",nullable = false,unique = true)
    private String sourceAddr;

    // getters and setters
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public String getSourceIp() {
        return sourceIp;
    }
    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
    public String getSourceAddr() {
        return sourceAddr;
    }
    public void setSourceAddr(String sourceAddr){
        this.sourceAddr = sourceAddr;
    }
}
