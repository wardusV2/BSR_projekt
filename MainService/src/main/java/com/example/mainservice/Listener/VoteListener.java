package com.example.mainservice.Listener;

import com.example.mainservice.Config.RabbitMQConfig;
import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.Service.MonitoringLogService;
import com.example.mainservice.Service.VoteAggregatorService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Odbiera głosy satelit z kolejki RabbitMQ.
 * Zastępuje poprzedni @MessageMapping("/from-service") w MainServiceController.
 *
 * Routing key wysyłany przez satelitę: vote.serviceX
 * Pozwala zidentyfikować nadawcę niezależnie od treści wiadomości.
 */
@Component
public class VoteListener {
    private static final Logger logger = LoggerFactory.getLogger(VoteListener.class);

    private final VoteAggregatorService aggregatorService;
    private final MonitoringLogService logService;

    public VoteListener(VoteAggregatorService aggregatorService,  MonitoringLogService logService) {
        this.aggregatorService = aggregatorService;
        this.logService = logService;
    }

    @RabbitListener(
            queues = RabbitMQConfig.VOTES_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onVoteReceived(ServiceMessage message,
                               @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {

        logService.log(Map.of(
                "type", "RABBIT_IN",
                "service", message.getServiceName(),
                "routingKey", routingKey,
                "weight", message.getWeight(),
                "content", message.getContent(),
                "timestamp", System.currentTimeMillis()
        ));

        try {
            aggregatorService.processVote(message);
        } catch (Exception e) {
            logger.error("Błąd przetwarzania głosu od {}: {}",
                    message.getServiceName(), e.getMessage(), e);
            // Rzucenie wyjątku → wiadomość trafi do DLX (dead letter queue)
            throw e;
        }
    }
}
