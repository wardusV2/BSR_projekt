package com.example.service3.Config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RabbitMQSatelliteConfigTest {

    @InjectMocks
    private RabbitMQSatelliteConfig config;

    @Test
    void votesExchange_shouldHaveCorrectValue() {
        assertThat(RabbitMQSatelliteConfig.VOTES_EXCHANGE).isEqualTo("votes.topic");
    }

    @Test
    void jsonMessageConverter_shouldReturnJackson2JsonMessageConverter() {
        MessageConverter converter = config.jsonMessageConverter();

        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void rabbitTemplate_shouldReturnConfiguredTemplate() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertThat(template).isNotNull();
        assertThat(template.getMessageConverter())
                .isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void rabbitTemplate_shouldUseProvidedConnectionFactory() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    }
}
