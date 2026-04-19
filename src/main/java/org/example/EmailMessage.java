package org.example;

import java.sql.Timestamp;

public class EmailMessage {
    private int id;
    private String sender;
    private String recipient;
    private String subject;
    private Timestamp date;
    private boolean isRead;
    private String content;

    public EmailMessage(int id, String sender, String recipient, String subject, Timestamp date, boolean isRead, String content) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.date = date;
        this.isRead = isRead;
        this.content = content;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public Timestamp getDate() { return date; }
    public boolean isRead() { return isRead; }
    public String getContent() { return content; }

    public String toRawString() {
        return "From: " + sender + "\r\n" +
               "To: " + recipient + "\r\n" +
               "Subject: " + subject + "\r\n" +
               "Date: " + date.toString() + "\r\n\r\n" +
               content;
    }

    public int length() {
        return toRawString().getBytes().length;
    }
}
