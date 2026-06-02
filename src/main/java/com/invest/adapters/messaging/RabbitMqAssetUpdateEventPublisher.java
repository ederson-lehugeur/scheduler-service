package com.invest.adapters.messaging;

import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.infrastructure.config.AssetUpdateRabbitMqConfig;
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
public class RabbitMqAssetUpdateEventPublisher implements AssetUpdateEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqAssetUpdateEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqAssetUpdateEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Retryable(
            retryFor = AmqpException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public void publish(UpdateAssetsEvent event) {
        log.info("Publishing UpdateAssetsEvent correlationId={} tickerCount={}",
                event.correlationId(), event.data().assets().size());

        rabbitTemplate.convertAndSend(
                AssetUpdateRabbitMqConfig.EXCHANGE_NAME,
                AssetUpdateRabbitMqConfig.ROUTING_KEY,
                event
        );

        log.debug("Successfully published UpdateAssetsEvent correlationId={} tickerCount={}",
                event.correlationId(), event.data().assets().size());
    }

    @Recover
    public void recover(AmqpException exception, UpdateAssetsEvent event) {
        // TODO - MDC depende da thread atual. Avaliar usar o event.correlationId().
        String correlationId = MDC.get("correlationId");
        log.error("Failed to publish UpdateAssetsEvent after retries exhausted - "
                        + "correlationId={} tickerCount={} reason={}",
                correlationId,
                event.data().assets().size(),
                exception.getMessage());
        // TODO - Avaliar o reenvio da exceção para não perder o evento gerado para envio.

        // TODO - Avaliar salvar em outbox.
        // outboxService.save(event);
    }
}
