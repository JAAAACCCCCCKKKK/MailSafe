package com.example.MailSafe.dto;

import com.example.MailSafe.models.Attachment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRequest {
    private String rawEmailBase64;

    /** 可选元数据 */
    private String messageId;
    private String sourceAddr;
    private String sourceIp;

    private List<AttachmentDto> attachments;

    public String getRawEmailBase64() { return rawEmailBase64; }
    public void setRawEmailBase64(String rawEmailBase64) { this.rawEmailBase64 = rawEmailBase64; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public String getSourceAddr() { return sourceAddr; }

    public void setSourceAddr(String sourceAddr) { this.sourceAddr = sourceAddr; }

    public List<AttachmentDto> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentDto> attachments) { this.attachments = attachments; }
}
