package com.example.service6.Config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMQSatelliteConfigTest {

    private final RabbitMQSatelliteConfig config =
            new RabbitMQSatelliteConfig();

    @Test
    void shouldCreateJsonConverter() {
        assertThat(config.jsonMessageConverter())
                .isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void shouldCreateRabbitTemplate() {

        ConnectionFactory factory =
                mock(ConnectionFactory.class);

        RabbitTemplate template =
                config.rabbitTemplate(factory);

        assertThat(template.getMessageConverter())
                .isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void shouldContainCorrectExchangeName() {
        assertThat(RabbitMQSatelliteConfig.VOTES_EXCHANGE)
                .isEqualTo("votes.topic");
    }
}