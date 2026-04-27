package com.invest.adapters.persistence;

import com.invest.domain.entities.Alert;
import com.invest.domain.entities.AlertStatus;
import com.invest.domain.ports.out.AlertRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRepositoryAdapter implements AlertRepository {

    private final JpaAlertRepository jpaRepository;
    private final JpaUserRepository jpaUserRepository;
    private final JpaRuleRepository jpaRuleRepository;
    private final JpaRuleGroupRepository jpaRuleGroupRepository;

    public AlertRepositoryAdapter(JpaAlertRepository jpaRepository,
                                  JpaUserRepository jpaUserRepository,
                                  JpaRuleRepository jpaRuleRepository,
                                  JpaRuleGroupRepository jpaRuleGroupRepository) {
        this.jpaRepository = jpaRepository;
        this.jpaUserRepository = jpaUserRepository;
        this.jpaRuleRepository = jpaRuleRepository;
        this.jpaRuleGroupRepository = jpaRuleGroupRepository;
    }

    @Override
    public Alert save(Alert alert) {
        UserEntity user = jpaUserRepository.getReferenceById(alert.getUserId());
        RuleEntity rule = alert.getRuleId() != null
                ? jpaRuleRepository.getReferenceById(alert.getRuleId())
                : null;
        RuleGroupEntity group = alert.getGroupId() != null
                ? jpaRuleGroupRepository.getReferenceById(alert.getGroupId())
                : null;

        AlertEntity entity = AlertMapper.toEntity(alert, user, rule, group);
        AlertEntity saved = jpaRepository.save(entity);
        return AlertMapper.toDomain(saved);
    }

    @Override
    public boolean existsActiveAlert(Long ruleId, String ticker) {
        return jpaRepository.existsByRuleIdAndTickerAndStatus(
                ruleId, ticker, AlertStatus.PENDING);
    }

    @Override
    public boolean existsActiveAlertForGroup(Long groupId, String ticker) {
        return jpaRepository.existsByGroupIdAndTickerAndStatus(
                groupId, ticker, AlertStatus.PENDING);
    }
}
