package com.invest.domain.entities;

import com.invest.domain.entities.enumerator.IndicatorType;
import com.invest.domain.strategy.ComparisonStrategy;
import com.invest.domain.strategy.ComparisonStrategyFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Getter
@AllArgsConstructor
@Builder
public class Rule {

    @Setter private Long id;
    private Long userId;
    private String ticker;
    @Setter private Long groupId;
    @Setter private IndicatorType indicatorType;
    @Setter private ComparisonOperator operator;
    @Setter private BigDecimal targetValue;
    @Setter private boolean active;
    private LocalDateTime createdAt;
    @Setter private LocalDateTime updatedAt;

    public boolean evaluate(Asset asset) {
        Optional<BigDecimal> currentValue = asset.getValueByIndicator(this.indicatorType);
        if (currentValue.isEmpty()) {
            return false;
        }
        ComparisonStrategy strategy = ComparisonStrategyFactory.create(this.operator);
        return strategy.evaluate(currentValue.get(), this.targetValue);
    }
}
