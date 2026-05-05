package com.webproject.safelogin.service;

import com.webproject.safelogin.model.ChatMessage;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatMessage save(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now());
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getPublicMessages() {
        return chatMessageRepository.findByReceiverIsNullOrderByTimestampDesc();
    }

    public List<ChatMessage> getPrivateMessages(String userNick) {
        return chatMessageRepository.findByReceiverNickOrderByTimestampDesc(userNick);
    }

}
