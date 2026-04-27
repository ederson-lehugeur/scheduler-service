package com.invest.application;

import com.invest.application.usecases.EvaluateRulesUseCaseImpl;
import com.invest.domain.entities.*;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 3: Rule group triggers correct alert and event.
 *
 * For any rule group of size N where all rules are satisfied against the asset
 * and no active alert exists for that group/ticker combination, the
 * EvaluateRulesUseCaseImpl creates a PENDING alert AND publishes an AlertTriggeredEvent
 * where: conditions has exactly N elements (one per rule in the group), groupName equals
 * the group's name, notificationChannel is EMAIL, correlationId is non-null, and data
 * fields match the corresponding group/asset/user data.
 *
 * Validates: Requirements 2.6, 3.4, 3.7, 5.2
 */
class EvaluateRulesGroupProperties {

    @Property
    void ruleGroupTriggersCorrectAlertAndEvent(
            @ForAll("satisfiedGroupAndAsset") GroupAssetPair pair,
            @ForAll("users") User user,
            @ForAll("alertIds") long alertId) {

        RuleGroup group = pair.group();
        Asset asset = pair.asset();

        user.setId(group.getUserId());

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);

        EvaluateRulesUseCaseImpl useCase = new EvaluateRulesUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);

        // Rules in the group have groupId set, so findAllActive returns them
        // but they get filtered out of individual evaluation
        when(ruleRepository.findAllActive()).thenReturn(group.getRules());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of(group));
        when(assetRepository.findByTickers(Set.of(group.getTicker()))).thenReturn(List.of(asset));
        when(alertRepository.existsActiveAlertForGroup(group.getId(), group.getTicker())).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(alertId);
            return alert;
        });
        when(userRepository.findById(group.getUserId())).thenReturn(Optional.of(user));

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        // Verify alert saved with PENDING status, groupId set, ruleId null
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(savedAlert.getGroupId()).isEqualTo(group.getId());
        assertThat(savedAlert.getRuleId()).isNull();
        assertThat(savedAlert.getUserId()).isEqualTo(group.getUserId());
        assertThat(savedAlert.getTicker()).isEqualTo(group.getTicker());

        // Verify event published
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
        assertThat(data.currentPrice()).isEqualByComparingTo(asset.getCurrentPrice());
        assertThat(data.dividendYield()).isEqualByComparingTo(asset.getDividendYield());
        assertThat(data.pVp()).isEqualByComparingTo(asset.getPVp());
        assertThat(data.groupName()).isEqualTo(group.getName());

        // Conditions must have exactly N elements, one per rule in the group
        List<Rule> rules = group.getRules();
        assertThat(data.conditions()).hasSize(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            AlertCondition condition = data.conditions().get(i);
            Rule rule = rules.get(i);
            assertThat(condition.field()).isEqualTo(rule.getField());
            assertThat(condition.operator()).isEqualTo(rule.getOperator());
            assertThat(condition.targetValue()).isEqualByComparingTo(rule.getTargetValue());
        }
    }

    @Provide
    Arbitrary<GroupAssetPair> satisfiedGroupAndAsset() {
        Arbitrary<Integer> ruleCounts = Arbitraries.integers().between(1, 5);
        Arbitrary<Long> groupIds = Arbitraries.longs().between(1, 100_000);
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 100_000);
        Arbitrary<String> tickers = Arbitraries.strings().alpha().ofLength(5).map(String::toUpperCase);
        Arbitrary<String> groupNames = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> "Group-" + s);

        return Combinators.combine(ruleCounts, groupIds, userIds, tickers, groupNames)
                .flatAs((ruleCount, groupId, userId, ticker, groupName) ->
                        generateGroupAndAsset(ruleCount, groupId, userId, ticker, groupName));
    }

    private Arbitrary<GroupAssetPair> generateGroupAndAsset(
            int ruleCount, long groupId, long userId, String ticker, String groupName) {

        Arbitrary<RuleField> fields = Arbitraries.of(RuleField.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> assetValues = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);
        Arbitrary<BigDecimal> offsets = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(100))
                .ofScale(2);
        Arbitrary<Long> ruleIds = Arbitraries.longs().between(1, 100_000);

        // Generate a list of rule specs, each with its own field, operator, offset, and ruleId
        Arbitrary<List<RuleSpec>> ruleSpecs = Combinators.combine(fields, operators, offsets, ruleIds)
                .as(RuleSpec::new)
                .list().ofSize(ruleCount);

        // Combine with asset values for each field
        return Combinators.combine(ruleSpecs, assetValues, assetValues, assetValues)
                .as((specs, priceValue, divValue, pvpValue) -> {
                    Map<RuleField, BigDecimal> assetFieldValues = Map.of(
                            RuleField.PRICE, priceValue,
                            RuleField.DIVIDEND_YIELD, divValue,
                            RuleField.P_VP, pvpValue
                    );

                    List<Rule> rules = IntStream.range(0, specs.size())
                            .mapToObj(i -> {
                                RuleSpec spec = specs.get(i);
                                BigDecimal assetValue = assetFieldValues.get(spec.field());
                                BigDecimal targetValue = computeSatisfyingTarget(
                                        spec.operator(), assetValue, spec.offset());

                                return Rule.builder()
                                        .id(spec.ruleId() + i)
                                        .userId(userId)
                                        .ticker(ticker)
                                        .groupId(groupId)
                                        .field(spec.field())
                                        .operator(spec.operator())
                                        .targetValue(targetValue)
                                        .active(true)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                            })
                            .toList();

                    RuleGroup group = RuleGroup.builder()
                            .id(groupId)
                            .userId(userId)
                            .ticker(ticker)
                            .name(groupName)
                            .rules(rules)
                            .createdAt(LocalDateTime.now())
                            .build();

                    Asset asset = Asset.builder()
                            .id(groupId + 1000)
                            .ticker(ticker)
                            .name("Asset-" + ticker)
                            .currentPrice(priceValue)
                            .dividendYield(divValue)
                            .pVp(pvpValue)
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return new GroupAssetPair(group, asset);
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
                                                      BigDecimal offset) {
        BigDecimal positiveOffset = offset.abs().add(BigDecimal.ONE);
        return switch (operator) {
            case GREATER_THAN -> assetValue.subtract(positiveOffset);
            case GREATER_THAN_OR_EQUAL -> assetValue;
            case LESS_THAN -> assetValue.add(positiveOffset);
            case LESS_THAN_OR_EQUAL -> assetValue;
            case EQUAL -> assetValue;
        };
    }

    record GroupAssetPair(RuleGroup group, Asset asset) {}

    record RuleSpec(RuleField field, ComparisonOperator operator, BigDecimal offset, long ruleId) {}
}
