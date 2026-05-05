package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.ChatMessage;
import com.webproject.safelogin.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class ChatController {


    @Autowired
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatService chatService) {
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
    }

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatMessage sendMessage(@Payload ChatMessage message) {
        return chatService.save(message);
    }

    @MessageMapping("/private")
    public void sendPrivateMessage(@Payload ChatMessage message) {
        chatService.save(message);
        messagingTemplate.convertAndSendToUser(
                message.getReceiver().getNick(),
                "/queue/private",
                message
        );
    }
//    @GetMapping("/api/chat/public")
//    public ResponseEntity<List<ChatMessage>> getPublicMessages() {
//        return ResponseEntity.ok(chatService.getRecentPublicMessages(50));
//    }

    @GetMapping("/api/chat/private/{userNick}")
    public ResponseEntity<List<ChatMessage>> getPrivateMessages(@PathVariable String userNick) {
        return ResponseEntity.ok(chatService.getPrivateMessages(userNick));
    }
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/messages")
    public Map<String, Object> sendMessage(@Payload Map<String, Object> chatMessage) {
        System.out.println("📨 Otrzymano wiadomość: " + chatMessage);
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/messages")
    public Map<String, Object> addUser(@Payload Map<String, Object> chatMessage,
                                       SimpMessageHeaderAccessor headerAccessor) {
        System.out.println("👤 Dodano użytkownika: " + chatMessage);
        String username = (String) chatMessage.get("sender");
        headerAccessor.getSessionAttributes().put("username", username);
        return chatMessage;
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload Map<String, Object> message, Principal principal) {
        System.out.println("🔒 Prywatna wiadomość: " + message);
        String targetUser = (String) message.get("targetUser");
        messagingTemplate.convertAndSendToUser(targetUser, "/queue/private", message);
    }
}

// Dodatkowy REST controller do testowania
@RestController
class WebSocketTestController {

    @GetMapping("/ws-test")
    public Map<String, String> testWebSocket() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "WebSocket endpoint is accessible");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return response;
    }
}