package com.invest.domain.ports.out;

import com.invest.domain.entities.Alert;

public interface AlertRepository {

    Alert save(Alert alert);

    boolean existsActiveAlert(Long ruleId, String ticker);

    boolean existsActiveAlertForGroup(Long groupId, String ticker);
}
