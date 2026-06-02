package com.invest.adapters.messaging;

import com.invest.domain.entities.ComparisonOperator;
import com.invest.domain.entities.IndicatorValue;
import com.invest.domain.entities.enumerator.IndicatorType;
import com.invest.domain.events.AlertCondition;
import com.invest.domain.events.AlertTriggeredEvent;
import com.invest.domain.events.NotificationChannel;
import com.invest.infrastructure.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RabbitMqEventPublisher rabbitMqEventPublisher;

    @Test
    void shouldPublishEventToCorrectExchangeAndRoutingKey() {
        AlertTriggeredEvent event = buildSampleEvent();

        rabbitMqEventPublisher.publish(event);

        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.ROUTING_KEY,
                event
        );
    }

    @Test
    void shouldPassEventAsMessageBody() {
        AlertTriggeredEvent event = buildSampleEvent();

        rabbitMqEventPublisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.ROUTING_KEY,
                event
        );
    }

    private AlertTriggeredEvent buildSampleEvent() {
        String now = LocalDateTime.now().toString();
        AlertCondition condition = new AlertCondition(
                IndicatorType.PRICE,
                ComparisonOperator.LESS_THAN,
                new BigDecimal("60.00")
        );
        List<IndicatorValue> indicatorValues = List.of(
                new IndicatorValue(IndicatorType.PRICE, new BigDecimal("56.78")),
                new IndicatorValue(IndicatorType.DIVIDEND_YIELD, new BigDecimal("8.5")),
                new IndicatorValue(IndicatorType.PVP, new BigDecimal("0.85"))
        );
        AlertTriggeredEvent.Data data = new AlertTriggeredEvent.Data(
                42L,
                1L,
                "user@example.com",
                "Banco do Brasil",
                "BBAS3",
                indicatorValues,
                null,
                List.of(condition),
                now
        );
        return new AlertTriggeredEvent(
                "ALERT_TRIGGERED",
                "550e8400-e29b-41d4-a716-446655440000",
                now,
                NotificationChannel.EMAIL,
                data
        );
    }
}
