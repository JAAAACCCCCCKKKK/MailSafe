package com.example.MailSafe.utils;

import com.example.MailSafe.dto.AttachmentDto;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing raw MIME messages and extracting useful metadata.
 */
public final class EmailParser {
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private EmailParser() {
    }

    public static ParsedEmail parse(byte[] rawEmailBytes) throws MessagingException, IOException {
        Objects.requireNonNull(rawEmailBytes, "rawEmailBytes");
        MimeMessage message = buildMimeMessage(rawEmailBytes);
        String rawEmail = new String(rawEmailBytes, StandardCharsets.ISO_8859_1);
        String messageId = extractMessageId(message);
        String sourceAddr = extractSourceAddress(message);
        String sourceIp = extractSourceIp(message);
        List<AttachmentDto> attachments = new ArrayList<>();
        extractAttachments(message, attachments);
        return new ParsedEmail(rawEmail, messageId, sourceAddr, sourceIp, Collections.unmodifiableList(attachments));
    }

    private static MimeMessage buildMimeMessage(byte[] rawEmailBytes) throws MessagingException {
        Session session = Session.getInstance(new Properties());
        try (InputStream inputStream = new ByteArrayInputStream(rawEmailBytes)) {
            return new MimeMessage(session, inputStream);
        } catch (IOException ex) {
            throw new MessagingException("Failed to read MIME message", ex);
        }
    }

    private static String extractMessageId(MimeMessage message) throws MessagingException {
        String messageId = message.getMessageID();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return message.getHeader("Message-ID", null);
    }

    private static String extractSourceAddress(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from != null && from.length > 0) {
            Address address = from[0];
            if (address instanceof InternetAddress internetAddress) {
                return internetAddress.getAddress();
            }
            return address.toString();
        }
        return null;
    }

    private static String extractSourceIp(MimeMessage message) throws MessagingException {
        String[] receivedHeaders = message.getHeader("Received");
        if (receivedHeaders != null) {
            for (String header : receivedHeaders) {
                Matcher matcher = IPV4_PATTERN.matcher(header);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }

        String[] originatingHeaders = message.getHeader("X-Originating-IP");
        if (originatingHeaders != null) {
            for (String header : originatingHeaders) {
                Matcher matcher = IPV4_PATTERN.matcher(header);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    private static void extractAttachments(Part part, List<AttachmentDto> attachments) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractAttachments(multipart.getBodyPart(i), attachments);
            }
            return;
        }

        String disposition = part.getDisposition();
        String fileName = part.getFileName();
        boolean hasFileName = fileName != null && !fileName.isBlank();
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || Part.INLINE.equalsIgnoreCase(disposition);
        if (!hasFileName && !isAttachment) {
            return;
        }

        String decodedFileName = hasFileName ? MimeUtility.decodeText(fileName) : "attachment";
        String contentType = part.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        try (InputStream dataStream = part.getInputStream()) {
            byte[] data = dataStream.readAllBytes();
            attachments.add(new AttachmentDto(decodedFileName, contentType, data));
        }
    }

    public record ParsedEmail(String rawEmail, String messageId, String sourceAddr, String sourceIp,
                              List<AttachmentDto> attachments) {
    }
}

