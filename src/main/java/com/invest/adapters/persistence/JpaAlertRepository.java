package com.invest.adapters.persistence;

import com.invest.domain.entities.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAlertRepository extends JpaRepository<AlertEntity, Long> {

    boolean existsByRuleIdAndTickerAndStatus(Long ruleId, String ticker, AlertStatus status);

    boolean existsByGroupIdAndTickerAndStatus(Long groupId, String ticker, AlertStatus status);
}
