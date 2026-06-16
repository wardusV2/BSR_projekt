package com.example.service1.Config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private final WebSocketConfig webSocketConfig = new WebSocketConfig();

    @Test
    void registerStompEndpoints_shouldAddSatelliteWsEndpointWithSockJS() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/satellite-ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);

        webSocketConfig.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/satellite-ws");
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
    }

    @Test
    void configureMessageBroker_shouldEnableSimpleBrokerOnTopicPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        webSocketConfig.configureMessageBroker(registry);
        verify(registry).enableSimpleBroker("/topic");
    }

    @Test
    void configureMessageBroker_shouldSetApplicationDestinationPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        webSocketConfig.configureMessageBroker(registry);
        verify(registry).setApplicationDestinationPrefixes("/app");
    }
}