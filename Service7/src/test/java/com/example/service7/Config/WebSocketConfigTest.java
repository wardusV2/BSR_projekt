package com.example.service7.Config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private final WebSocketConfig config =
            new WebSocketConfig();

    @Test
    void shouldConfigureBroker() {

        MessageBrokerRegistry registry =
                mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }
}