package com.invest.application;

import com.invest.application.usecases.EvaluateRulesUseCaseImpl;
import com.invest.domain.entities.*;
import com.invest.domain.entities.enumerator.AssetType;
import com.invest.domain.entities.enumerator.IndicatorType;
import com.invest.domain.events.AlertCondition;
import com.invest.domain.events.AlertTriggeredEvent;
import com.invest.domain.events.NotificationChannel;
import com.invest.domain.ports.out.*;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 2: Individual rule triggers correct alert and event.
 */
class EvaluateRulesIndividualRuleProperties {

    @Property
    void individualRuleTriggersCorrectAlertAndEvent(
            @ForAll("satisfiedRuleAndAsset") RuleAssetPair pair,
            @ForAll("users") User user,
            @ForAll("alertIds") long alertId) {

        Rule rule = pair.rule();
        Asset asset = pair.asset();

        user.setId(rule.getUserId());

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);

        EvaluateRulesUseCaseImpl useCase = new EvaluateRulesUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);

        when(ruleRepository.findAllActive()).thenReturn(List.of(rule));
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());
        when(assetRepository.findByTickers(Set.of(rule.getTicker()))).thenReturn(List.of(asset));
        when(alertRepository.existsActiveAlert(rule.getId(), rule.getTicker())).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(alertId);
            return alert;
        });
        when(userRepository.findById(rule.getUserId())).thenReturn(Optional.of(user));

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(savedAlert.getRuleId()).isEqualTo(rule.getId());
        assertThat(savedAlert.getUserId()).isEqualTo(rule.getUserId());
        assertThat(savedAlert.getTicker()).isEqualTo(rule.getTicker());
        assertThat(savedAlert.getGroupId()).isNull();

        ArgumentCaptor<AlertTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        AlertTriggeredEvent event = eventCaptor.getValue();

        assertThat(event.eventType()).isEqualTo("ALERT_TRIGGERED");
        assertThat(event.correlationId()).isNotNull();
        assertThat(event.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);

        AlertTriggeredEvent.Data data = event.data();
        assertThat(data.alertId()).isEqualTo(alertId);
        assertThat(data.userId()).isEqualTo(user.getId());
        assertThat(data.email()).isEqualTo(user.getEmail());
        assertThat(data.assetName()).isEqualTo(asset.getName());
        assertThat(data.ticker()).isEqualTo(asset.getTicker());
        assertThat(data.indicatorValues()).isEqualTo(asset.getIndicatorValues());
        assertThat(data.groupName()).isNull();

        assertThat(data.conditions()).hasSize(1);
        AlertCondition condition = data.conditions().get(0);
        assertThat(condition.indicatorType()).isEqualTo(rule.getIndicatorType());
        assertThat(condition.operator()).isEqualTo(rule.getOperator());
        assertThat(condition.targetValue()).isEqualByComparingTo(rule.getTargetValue());
    }

    @Provide
    Arbitrary<RuleAssetPair> satisfiedRuleAndAsset() {
        Arbitrary<IndicatorType> indicators = Arbitraries.of(IndicatorType.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> values = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 100_000);
        Arbitrary<String> tickers = Arbitraries.strings().alpha().ofLength(5).map(String::toUpperCase);

        return Combinators.combine(indicators, operators, values, values, ids, ids, tickers)
                .as((indicatorType, operator, assetValue, baseTarget, ruleId, userId, ticker) -> {
                    BigDecimal targetValue = computeSatisfyingTarget(operator, assetValue, baseTarget);

                    Rule rule = Rule.builder()
                            .id(ruleId)
                            .userId(userId)
                            .ticker(ticker)
                            .groupId(null)
                            .indicatorType(indicatorType)
                            .operator(operator)
                            .targetValue(targetValue)
                            .active(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    List<IndicatorValue> indicatorValues = List.of(
                            new IndicatorValue(indicatorType, assetValue)
                    );

                    Asset asset = Asset.builder()
                            .id(ruleId + 1000)
                            .ticker(ticker)
                            .name("Asset-" + ticker)
                            .assetType(AssetType.FII)
                            .indicatorValues(indicatorValues)
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return new RuleAssetPair(rule, asset);
                });
    }

    @Provide
    Arbitrary<User> users() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1, 100_000);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
        Arbitrary<String> emails = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> s.toLowerCase() + "@test.com");

        return Combinators.combine(ids, names, emails)
                .as((id, name, email) -> {
                    User user = new User(name, email, "hash");
                    user.setId(id);
                    return user;
                });
    }

    @Provide
    Arbitrary<Long> alertIds() {
        return Arbitraries.longs().between(1, 100_000);
    }

    private static BigDecimal computeSatisfyingTarget(ComparisonOperator operator,
                                                      BigDecimal assetValue,
                                                      BigDecimal baseTarget) {
        BigDecimal offset = baseTarget.abs().add(BigDecimal.ONE);
        return switch (operator) {
            case GREATER_THAN -> assetValue.subtract(offset);
            case GREATER_THAN_OR_EQUAL -> assetValue;
            case LESS_THAN -> assetValue.add(offset);
            case LESS_THAN_OR_EQUAL -> assetValue;
            case EQUAL -> assetValue;
        };
    }

    record RuleAssetPair(Rule rule, Asset asset) {}
}
