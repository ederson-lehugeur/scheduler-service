package com.invest.domain.strategy;

import com.invest.domain.entities.ComparisonOperator;
import net.jqwik.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: ComparisonStrategy evaluation correctness.
 *
 * For any ComparisonOperator and any two BigDecimal values, the ComparisonStrategy
 * returned by ComparisonStrategyFactory.create(operator) produces the same boolean
 * result as the equivalent BigDecimal.compareTo comparison for that operator.
 *
 * Validates: Requirements 2.4
 */
class ComparisonStrategyProperties {

    @Property
    void strategyEvaluationMatchesCompareTo(
            @ForAll("operators") ComparisonOperator operator,
            @ForAll("bigDecimals") BigDecimal currentValue,
            @ForAll("bigDecimals") BigDecimal targetValue) {

        ComparisonStrategy strategy = ComparisonStrategyFactory.create(operator);
        boolean actual = strategy.evaluate(currentValue, targetValue);
        boolean expected = expectedResult(operator, currentValue, targetValue);

        assertThat(actual)
                .as("operator=%s, currentValue=%s, targetValue=%s", operator, currentValue, targetValue)
                .isEqualTo(expected);
    }

    @Provide
    Arbitrary<ComparisonOperator> operators() {
        return Arbitraries.of(ComparisonOperator.values());
    }

    @Provide
    Arbitrary<BigDecimal> bigDecimals() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-1_000_000), BigDecimal.valueOf(1_000_000))
                .ofScale(4);
    }

    private boolean expectedResult(ComparisonOperator operator, BigDecimal currentValue, BigDecimal targetValue) {
        int cmp = currentValue.compareTo(targetValue);
        return switch (operator) {
            case GREATER_THAN -> cmp > 0;
            case LESS_THAN -> cmp < 0;
            case GREATER_THAN_OR_EQUAL -> cmp >= 0;
            case LESS_THAN_OR_EQUAL -> cmp <= 0;
            case EQUAL -> cmp == 0;
        };
    }
}
