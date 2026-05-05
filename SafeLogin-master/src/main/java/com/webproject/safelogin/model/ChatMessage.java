package com.webproject.safelogin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    private User receiver; // null dla wiadomości publicznych

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.PUBLIC;

    // Konstruktory
    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(String content, User sender) {
        this();
        this.content = content;
        this.sender = sender;
    }

    public ChatMessage(String content, User sender, User receiver) {
        this();
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
        this.type = MessageType.PRIVATE;
    }

    // Gettery i settery
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
        if (receiver != null) {
            this.type = MessageType.PRIVATE;
        }
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    // Metody pomocnicze
    public boolean isPrivate() {
        return receiver != null && type == MessageType.PRIVATE;
    }

    public boolean isPublic() {
        return receiver == null && type == MessageType.PUBLIC;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", sender=" + (sender != null ? sender.getNick() : "null") +
                ", receiver=" + (receiver != null ? receiver.getNick() : "null") +
                ", type=" + type +
                '}';
    }
}

enum MessageType {
    PUBLIC, PRIVATE, SYSTEM
}