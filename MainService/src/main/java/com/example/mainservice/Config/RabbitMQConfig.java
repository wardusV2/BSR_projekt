package com.example.mainservice.Config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // ── Exchange ────────────────────────────────────────────────────────────
    public static final String VOTES_EXCHANGE = "votes.topic";

    // ── Routing key pattern consumed by MainService ─────────────────────────
    // Satelity publikują pod: vote.service1, vote.service2, …
    // MainService subskrybuje: vote.#  (wszystkie)
    public static final String VOTES_ROUTING_PATTERN = "vote.#";

    // ── Queue ───────────────────────────────────────────────────────────────
    public static final String VOTES_QUEUE = "votes.main.queue";

    // ── Dead-letter (błędy byzantyjskie / poison messages) ─────────────────
    public static final String DLX_EXCHANGE = "votes.dlx";
    public static final String DLX_QUEUE    = "votes.dead.queue";

    /* ================================================================
       Exchange
       ================================================================ */

    @Bean
    public TopicExchange votesExchange() {
        return ExchangeBuilder
                .topicExchange(VOTES_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /* ================================================================
       Queues
       ================================================================ */

    @Bean
    public Queue votesQueue() {
        return QueueBuilder
                .durable(VOTES_QUEUE)
                // wiadomości odrzucone trafiają do DLX
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dead")
                // TTL 60 s – wiadomość starsza niż minuta jest uznawana za nieaktualną
                .withArgument("x-message-ttl", 60_000)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLX_QUEUE)
                .build();
    }

    /* ================================================================
       Bindings
       ================================================================ */

    @Bean
    public Binding votesBinding(Queue votesQueue, TopicExchange votesExchange) {
        return BindingBuilder
                .bind(votesQueue)
                .to(votesExchange)
                .with(VOTES_ROUTING_PATTERN);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue,
                                     DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with("dead");
    }

    /* ================================================================
       Serialization (JSON)
       ================================================================ */

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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // domyślny prefetch – 1 wiadomość na raz (fair dispatch)
        factory.setPrefetchCount(1);
        return factory;
    }
}
