package com.example.MailSafe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private String fileName;
    private String contentType;
    private byte[] data;

    public String getFileName(){ return fileName;}
    public void setFileName(String fileName){ this.fileName = fileName; }
    public String getContentType(){ return contentType; }
    public void setContentType(String contentType){ this.contentType = contentType; }
    public byte[] getData(){ return data; }
    public void setData(byte[] data){ this.data = data; }
}
