package com.webproject.safelogin.repository;

import com.webproject.safelogin.model.ChatMessage;
import com.webproject.safelogin.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySenderAndReceiver(User sender, User receiver);
    List<ChatMessage> findByReceiverAndSender(User receiver, User sender);
    List<ChatMessage> findByReceiverIsNullOrderByTimestampDesc();
    // Pobierz prywatne wiadomości dla użytkownika
    List<ChatMessage> findByReceiverNickOrderByTimestampDesc(String userNick);
}
