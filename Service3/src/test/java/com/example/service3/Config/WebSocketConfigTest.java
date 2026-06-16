package com.example.service3.Config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    private WebSocketConfig webSocketConfig;

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration stompRegistration;

    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfig();
    }

    @Test
    void registerStompEndpoints_shouldAddSatelliteWsEndpoint() {
        when(stompEndpointRegistry.addEndpoint("/satellite-ws"))
                .thenReturn(stompRegistration);
        when(stompRegistration.setAllowedOriginPatterns("*"))
                .thenReturn(stompRegistration);

        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        verify(stompEndpointRegistry).addEndpoint("/satellite-ws");
    }

    @Test
    void registerStompEndpoints_shouldAllowAllOrigins() {
        when(stompEndpointRegistry.addEndpoint("/satellite-ws"))
                .thenReturn(stompRegistration);
        when(stompRegistration.setAllowedOriginPatterns("*"))
                .thenReturn(stompRegistration);

        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        verify(stompRegistration).setAllowedOriginPatterns("*");
    }

    @Test
    void registerStompEndpoints_shouldEnableSockJS() {
        when(stompEndpointRegistry.addEndpoint("/satellite-ws"))
                .thenReturn(stompRegistration);
        when(stompRegistration.setAllowedOriginPatterns("*"))
                .thenReturn(stompRegistration);

        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        verify(stompRegistration).withSockJS();
    }

    @Test
    void configureMessageBroker_shouldEnableSimpleBrokerOnTopic() {
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/topic");
    }

    @Test
    void configureMessageBroker_shouldSetApplicationDestinationPrefix() {
        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).setApplicationDestinationPrefixes("/app");
    }
}
