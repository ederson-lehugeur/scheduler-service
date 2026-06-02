package com.invest.adapters.persistence;

import com.invest.domain.entities.Rule;
import com.invest.domain.entities.enumerator.IndicatorType;

public final class RuleMapper {

    private RuleMapper() {}

    public static Rule toDomain(RuleEntity entity) {
        Long groupId = entity.getGroup() != null ? entity.getGroup().getId() : null;
        return new Rule(
                entity.getId(),
                entity.getUser().getId(),
                entity.getAsset().getTicker(),
                groupId,
                IndicatorType.fromCode(entity.getIndicatorType()).orElseThrow(),
                entity.getOperator(),
                entity.getTargetValue(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
