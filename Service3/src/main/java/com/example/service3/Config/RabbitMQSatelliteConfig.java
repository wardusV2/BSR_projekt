package com.example.service3.Config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Konfiguracja RabbitMQ dla satelity.
 * Satelita TYLKO publikuje – nie konsumuje.
 * Exchange i kolejki deklaruje MainService.
 * Skopiuj do każdej satelity (service1…service7),
 * zmieniając tylko package na górze.
 */
@Configuration
public class RabbitMQSatelliteConfig {


    // Musi być identyczne z MainService RabbitMQConfig.VOTES_EXCHANGE
    public static final String VOTES_EXCHANGE = "votes.topic";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}