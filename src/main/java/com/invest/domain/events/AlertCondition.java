package com.invest.domain.events;

import com.invest.domain.entities.ComparisonOperator;
import com.invest.domain.entities.enumerator.IndicatorType;

import java.math.BigDecimal;

public record AlertCondition(
        IndicatorType indicatorType,
        ComparisonOperator operator,
        BigDecimal targetValue
) {
}
