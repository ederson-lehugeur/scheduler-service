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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 3: Rule group triggers correct alert and event.
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

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert savedAlert = alertCaptor.getValue();
        assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);
        assertThat(savedAlert.getGroupId()).isEqualTo(group.getId());
        assertThat(savedAlert.getRuleId()).isNull();
        assertThat(savedAlert.getUserId()).isEqualTo(group.getUserId());
        assertThat(savedAlert.getTicker()).isEqualTo(group.getTicker());

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
        assertThat(data.groupName()).isEqualTo(group.getName());

        List<Rule> rules = group.getRules();
        assertThat(data.conditions()).hasSize(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            AlertCondition condition = data.conditions().get(i);
            Rule rule = rules.get(i);
            assertThat(condition.indicatorType()).isEqualTo(rule.getIndicatorType());
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

        Arbitrary<IndicatorType> indicators = Arbitraries.of(IndicatorType.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> assetValues = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);
        Arbitrary<BigDecimal> offsets = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(100))
                .ofScale(2);
        Arbitrary<Long> ruleIds = Arbitraries.longs().between(1, 100_000);

        Arbitrary<List<RuleSpec>> ruleSpecs = Combinators.combine(indicators, operators, offsets, ruleIds)
                .as(RuleSpec::new)
                .list().ofSize(ruleCount);

        return Combinators.combine(ruleSpecs, assetValues)
                .as((specs, baseValue) -> {
                    // Build indicator values map for the asset - one per unique indicator type used
                    Map<IndicatorType, BigDecimal> indicatorMap = new HashMap<>();
                    for (RuleSpec spec : specs) {
                        indicatorMap.putIfAbsent(spec.indicatorType(), baseValue);
                    }

                    List<IndicatorValue> indicatorValues = indicatorMap.entrySet().stream()
                            .map(e -> new IndicatorValue(e.getKey(), e.getValue()))
                            .toList();

                    List<Rule> rules = IntStream.range(0, specs.size())
                            .mapToObj(i -> {
                                RuleSpec spec = specs.get(i);
                                BigDecimal assetValue = indicatorMap.get(spec.indicatorType());
                                BigDecimal targetValue = computeSatisfyingTarget(
                                        spec.operator(), assetValue, spec.offset());

                                return Rule.builder()
                                        .id(spec.ruleId() + i)
                                        .userId(userId)
                                        .ticker(ticker)
                                        .groupId(groupId)
                                        .indicatorType(spec.indicatorType())
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
                            .assetType(AssetType.FII)
                            .indicatorValues(indicatorValues)
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

    record RuleSpec(IndicatorType indicatorType, ComparisonOperator operator, BigDecimal offset, long ruleId) {}
}
