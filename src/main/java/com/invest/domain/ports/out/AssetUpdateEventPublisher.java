package com.invest.domain.ports.out;

import com.invest.domain.events.UpdateAssetsEvent;

public interface AssetUpdateEventPublisher {

    void publish(UpdateAssetsEvent event);
}
