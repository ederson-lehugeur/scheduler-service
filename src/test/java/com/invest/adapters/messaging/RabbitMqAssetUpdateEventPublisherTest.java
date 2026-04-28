package com.invest.adapters.messaging;

import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.infrastructure.config.AssetUpdateRabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Validates: Requirements 4.2, 6.1
 */
@ExtendWith(MockitoExtension.class)
class RabbitMqAssetUpdateEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMqAssetUpdateEventPublisher rabbitMqAssetUpdateEventPublisher;

    @Test
    void shouldPublishEventToCorrectExchangeAndRoutingKey() {
        UpdateAssetsEvent event = buildSampleEvent();

        rabbitMqAssetUpdateEventPublisher.publish(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                AssetUpdateRabbitMqConfig.EXCHANGE_NAME,
                AssetUpdateRabbitMqConfig.ROUTING_KEY,
                event
        );
    }

    @Test
    void shouldPassEventAsMessageBody() {
        UpdateAssetsEvent event = buildSampleEvent();

        rabbitMqAssetUpdateEventPublisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                AssetUpdateRabbitMqConfig.EXCHANGE_NAME,
                AssetUpdateRabbitMqConfig.ROUTING_KEY,
                event
        );
    }

    private UpdateAssetsEvent buildSampleEvent() {
        return new UpdateAssetsEvent(
                "UPDATE_ASSETS",
                "550e8400-e29b-41d4-a716-446655440000",
                new UpdateAssetsEvent.Data(List.of("PETR4", "VALE3", "ITUB4"))
        );
    }
}
