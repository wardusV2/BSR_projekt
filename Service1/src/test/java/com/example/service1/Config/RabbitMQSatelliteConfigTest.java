package com.example.service1.Config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMQSatelliteConfigTest {

    private final RabbitMQSatelliteConfig config = new RabbitMQSatelliteConfig();

    @Test
    void votesExchangeConstant_shouldBeCorrectValue() {
        assertThat(RabbitMQSatelliteConfig.VOTES_EXCHANGE).isEqualTo("votes.topic");
    }

    @Test
    void jsonMessageConverter_shouldReturnJackson2JsonMessageConverter() {
        MessageConverter converter = config.jsonMessageConverter();
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void rabbitTemplate_shouldReturnNonNullTemplate() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);
        assertThat(template).isNotNull();
    }

    @Test
    void rabbitTemplate_shouldUseJsonMessageConverter() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}