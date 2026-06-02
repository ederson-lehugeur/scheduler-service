package com.invest.domain.events;

import com.invest.domain.entities.IndicatorValue;

import java.util.List;

public record AlertTriggeredEvent(
        String eventType,
        String correlationId,
        String timestamp,
        NotificationChannel notificationChannel,
        Data data
) {

    public record Data(
            long alertId,
            long userId,
            String email,
            String assetName,
            String ticker,
            List<IndicatorValue> indicatorValues,
            String groupName,
            List<AlertCondition> conditions,
            String evaluatedAt
    ) {
    }
}
