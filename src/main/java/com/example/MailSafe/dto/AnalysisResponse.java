package com.example.MailSafe.dto;


public class AnalysisResponse {
    private String messageId;
    private String sourceIp;
    private String sourceAddr;
    private String[] attachments;
    private int riskScore;

    public AnalysisResponse(String messageId, String sourceIp, String sourceAddr, String[] attachments, int riskScore) {
        this.messageId = messageId;
        this.sourceIp = sourceIp;
        this.sourceAddr = sourceAddr;
        this.attachments = attachments;
        this.riskScore = riskScore;
    }
    public String getMessageId() {
        return messageId;
    }
    public String getSourceIp() {
        return sourceIp;
    }
    public String getSourceAddr() {
        return sourceAddr;
    }
    public String[] getAttachments() {
        return attachments;
    }
    public int getRiskScore() {
        return riskScore;
    }
}
