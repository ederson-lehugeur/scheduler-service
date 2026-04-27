package com.invest.domain.ports.out;

import com.invest.domain.events.AlertTriggeredEvent;

public interface EventPublisher {

    void publish(AlertTriggeredEvent event);
}
