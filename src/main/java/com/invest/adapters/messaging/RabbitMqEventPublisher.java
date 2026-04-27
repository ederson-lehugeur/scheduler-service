package com.invest.adapters.messaging;

import com.invest.domain.events.AlertTriggeredEvent;
import com.invest.domain.ports.out.EventPublisher;
import com.invest.infrastructure.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Retryable(
            retryFor = AmqpException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public void publish(AlertTriggeredEvent event) {
        log.info("Publishing AlertTriggeredEvent correlationId={} alertId={}",
                event.correlationId(), event.data().alertId());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.ROUTING_KEY,
                event
        );

        log.debug("Successfully published AlertTriggeredEvent correlationId={} alertId={}",
                event.correlationId(), event.data().alertId());
    }

    @Recover
    public void recover(AmqpException exception, AlertTriggeredEvent event) {
        String correlationId = MDC.get("correlationId");
        log.error("Failed to publish AlertTriggeredEvent after retries exhausted - "
                        + "correlationId={} alertId={} reason={}",
                correlationId,
                event.data().alertId(),
                exception.getMessage());
    }
}
