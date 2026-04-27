package com.invest.application;

import com.invest.application.usecases.EvaluateRulesUseCaseImpl;
import com.invest.domain.entities.*;
import com.invest.domain.ports.out.*;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 4: Error isolation across rule evaluations.
 *
 * For any set of individual rules and rule groups where some evaluations throw exceptions,
 * the EvaluateRulesUseCaseImpl continues evaluating all remaining rules and groups without
 * interrupting the job execution. The number of successfully evaluated rules/groups equals
 * the total minus the failing ones.
 *
 * Validates: Requirements 2.7
 */
class EvaluateRulesErrorIsolationProperties {

    @Property
    void failingIndividualRulesDoNotPreventGoodRulesFromBeingEvaluated(
            @ForAll("ruleSetWithFailures") RuleSetWithFailures ruleSet,
            @ForAll("users") User user) {

        user.setId(ruleSet.userId());

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);

        EvaluateRulesUseCaseImpl useCase = new EvaluateRulesUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);

        when(ruleRepository.findAllActive()).thenReturn(ruleSet.allRules());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());
        when(assetRepository.findByTickers(anySet())).thenReturn(ruleSet.assets());

        // For failing rules, existsActiveAlert throws RuntimeException
        for (Rule failingRule : ruleSet.failingRules()) {
            when(alertRepository.existsActiveAlert(eq(failingRule.getId()), eq(failingRule.getTicker())))
                    .thenThrow(new RuntimeException("Simulated failure for rule " + failingRule.getId()));
        }

        // For good rules, existsActiveAlert returns false (no existing alert)
        for (Rule goodRule : ruleSet.goodRules()) {
            when(alertRepository.existsActiveAlert(eq(goodRule.getId()), eq(goodRule.getTicker())))
                    .thenReturn(false);
        }

        AtomicLong alertIdCounter = new AtomicLong(1);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(alertIdCounter.getAndIncrement());
            return alert;
        });
        when(userRepository.findById(ruleSet.userId())).thenReturn(Optional.of(user));

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        // The key property: save() is called exactly once per good rule
        int expectedSaves = ruleSet.goodRules().size();
        verify(alertRepository, times(expectedSaves)).save(any(Alert.class));

        // Verify each good rule's alert was saved
        if (expectedSaves > 0) {
            ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, times(expectedSaves)).save(alertCaptor.capture());

            Set<Long> savedRuleIds = new HashSet<>();
            for (Alert savedAlert : alertCaptor.getAllValues()) {
                assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);
                savedRuleIds.add(savedAlert.getRuleId());
            }

            Set<Long> expectedRuleIds = new HashSet<>();
            for (Rule goodRule : ruleSet.goodRules()) {
                expectedRuleIds.add(goodRule.getId());
            }
            assertThat(savedRuleIds).isEqualTo(expectedRuleIds);
        }

        // Verify event published for each good rule
        verify(eventPublisher, times(expectedSaves)).publish(any());
    }

    @Property
    void failingRuleGroupsDoNotPreventGoodGroupsFromBeingEvaluated(
            @ForAll("groupSetWithFailures") GroupSetWithFailures groupSet,
            @ForAll("users") User user) {

        user.setId(groupSet.userId());

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);

        EvaluateRulesUseCaseImpl useCase = new EvaluateRulesUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);

        // All rules in groups have groupId set, so they are filtered out of individual evaluation
        when(ruleRepository.findAllActive()).thenReturn(List.of());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(groupSet.allGroups());
        when(assetRepository.findByTickers(anySet())).thenReturn(groupSet.assets());

        // For failing groups, existsActiveAlertForGroup throws RuntimeException
        for (RuleGroup failingGroup : groupSet.failingGroups()) {
            when(alertRepository.existsActiveAlertForGroup(eq(failingGroup.getId()), eq(failingGroup.getTicker())))
                    .thenThrow(new RuntimeException("Simulated failure for group " + failingGroup.getId()));
        }

        // For good groups, existsActiveAlertForGroup returns false
        for (RuleGroup goodGroup : groupSet.goodGroups()) {
            when(alertRepository.existsActiveAlertForGroup(eq(goodGroup.getId()), eq(goodGroup.getTicker())))
                    .thenReturn(false);
        }

        AtomicLong alertIdCounter = new AtomicLong(1);
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(alertIdCounter.getAndIncrement());
            return alert;
        });
        when(userRepository.findById(groupSet.userId())).thenReturn(Optional.of(user));

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        // The key property: save() is called exactly once per good group
        int expectedSaves = groupSet.goodGroups().size();
        verify(alertRepository, times(expectedSaves)).save(any(Alert.class));

        // Verify each good group's alert was saved
        if (expectedSaves > 0) {
            ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository, times(expectedSaves)).save(alertCaptor.capture());

            Set<Long> savedGroupIds = new HashSet<>();
            for (Alert savedAlert : alertCaptor.getAllValues()) {
                assertThat(savedAlert.getStatus()).isEqualTo(AlertStatus.PENDING);
                savedGroupIds.add(savedAlert.getGroupId());
            }

            Set<Long> expectedGroupIds = new HashSet<>();
            for (RuleGroup goodGroup : groupSet.goodGroups()) {
                expectedGroupIds.add(goodGroup.getId());
            }
            assertThat(savedGroupIds).isEqualTo(expectedGroupIds);
        }

        // Verify event published for each good group
        verify(eventPublisher, times(expectedSaves)).publish(any());
    }

    // --- Providers ---

    @Provide
    Arbitrary<RuleSetWithFailures> ruleSetWithFailures() {
        Arbitrary<Integer> totalCounts = Arbitraries.integers().between(2, 5);
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 100_000);
        Arbitrary<String> tickers = Arbitraries.strings().alpha().ofLength(5).map(String::toUpperCase);

        return Combinators.combine(totalCounts, userIds, tickers)
                .flatAs((totalCount, userId, ticker) ->
                        generateRuleSetWithFailures(totalCount, userId, ticker));
    }

    private Arbitrary<RuleSetWithFailures> generateRuleSetWithFailures(
            int totalCount, long userId, String ticker) {

        Arbitrary<RuleField> fields = Arbitraries.of(RuleField.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> assetValues = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);
        Arbitrary<BigDecimal> offsets = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(100))
                .ofScale(2);

        // Generate a bitmask to decide which rules fail (at least 1 good, at least 1 failing)
        Arbitrary<List<Boolean>> failFlags = Arbitraries.of(true, false)
                .list().ofSize(totalCount)
                .filter(flags -> flags.contains(true) && flags.contains(false));

        Arbitrary<List<RuleSpec>> ruleSpecs = Combinators.combine(fields, operators, offsets)
                .as(RuleSpec::new)
                .list().ofSize(totalCount);

        return Combinators.combine(ruleSpecs, failFlags, assetValues, assetValues, assetValues)
                .as((specs, flags, priceValue, divValue, pvpValue) -> {
                    Map<RuleField, BigDecimal> assetFieldValues = Map.of(
                            RuleField.PRICE, priceValue,
                            RuleField.DIVIDEND_YIELD, divValue,
                            RuleField.P_VP, pvpValue
                    );

                    List<Rule> allRules = new ArrayList<>();
                    List<Rule> goodRules = new ArrayList<>();
                    List<Rule> failingRules = new ArrayList<>();

                    for (int i = 0; i < specs.size(); i++) {
                        RuleSpec spec = specs.get(i);
                        BigDecimal assetValue = assetFieldValues.get(spec.field());
                        BigDecimal targetValue = computeSatisfyingTarget(
                                spec.operator(), assetValue, spec.offset());

                        Rule rule = Rule.builder()
                                .id((long) (i + 1))
                                .userId(userId)
                                .ticker(ticker)
                                .groupId(null)
                                .field(spec.field())
                                .operator(spec.operator())
                                .targetValue(targetValue)
                                .active(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        allRules.add(rule);
                        if (flags.get(i)) {
                            failingRules.add(rule);
                        } else {
                            goodRules.add(rule);
                        }
                    }

                    Asset asset = Asset.builder()
                            .id(1L)
                            .ticker(ticker)
                            .name("Asset-" + ticker)
                            .currentPrice(priceValue)
                            .dividendYield(divValue)
                            .pVp(pvpValue)
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return new RuleSetWithFailures(
                            allRules, goodRules, failingRules, List.of(asset), userId);
                });
    }

    @Provide
    Arbitrary<GroupSetWithFailures> groupSetWithFailures() {
        Arbitrary<Integer> totalCounts = Arbitraries.integers().between(2, 5);
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 100_000);
        Arbitrary<String> tickers = Arbitraries.strings().alpha().ofLength(5).map(String::toUpperCase);

        return Combinators.combine(totalCounts, userIds, tickers)
                .flatAs((totalCount, userId, ticker) ->
                        generateGroupSetWithFailures(totalCount, userId, ticker));
    }

    private Arbitrary<GroupSetWithFailures> generateGroupSetWithFailures(
            int totalCount, long userId, String ticker) {

        Arbitrary<RuleField> fields = Arbitraries.of(RuleField.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> offsets = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(100))
                .ofScale(2);

        // Each group has 1-3 rules
        Arbitrary<Integer> ruleCounts = Arbitraries.integers().between(1, 3);

        // At least 1 good, at least 1 failing
        Arbitrary<List<Boolean>> failFlags = Arbitraries.of(true, false)
                .list().ofSize(totalCount)
                .filter(flags -> flags.contains(true) && flags.contains(false));

        Arbitrary<BigDecimal> assetValues = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);

        return Combinators.combine(failFlags, ruleCounts, fields, operators, offsets,
                        assetValues, assetValues, assetValues)
                .as((flags, ruleCount, field, operator, offset,
                     priceValue, divValue, pvpValue) -> {

                    Map<RuleField, BigDecimal> assetFieldValues = Map.of(
                            RuleField.PRICE, priceValue,
                            RuleField.DIVIDEND_YIELD, divValue,
                            RuleField.P_VP, pvpValue
                    );

                    List<RuleGroup> allGroups = new ArrayList<>();
                    List<RuleGroup> goodGroups = new ArrayList<>();
                    List<RuleGroup> failingGroups = new ArrayList<>();

                    for (int g = 0; g < flags.size(); g++) {
                        long groupId = g + 1;

                        List<Rule> rules = new ArrayList<>();
                        for (int r = 0; r < ruleCount; r++) {
                            BigDecimal assetValue = assetFieldValues.get(field);
                            BigDecimal targetValue = computeSatisfyingTarget(operator, assetValue, offset);

                            rules.add(Rule.builder()
                                    .id(groupId * 100 + r)
                                    .userId(userId)
                                    .ticker(ticker)
                                    .groupId(groupId)
                                    .field(field)
                                    .operator(operator)
                                    .targetValue(targetValue)
                                    .active(true)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build());
                        }

                        RuleGroup group = RuleGroup.builder()
                                .id(groupId)
                                .userId(userId)
                                .ticker(ticker)
                                .name("Group-" + groupId)
                                .rules(rules)
                                .createdAt(LocalDateTime.now())
                                .build();

                        allGroups.add(group);
                        if (flags.get(g)) {
                            failingGroups.add(group);
                        } else {
                            goodGroups.add(group);
                        }
                    }

                    Asset asset = Asset.builder()
                            .id(1L)
                            .ticker(ticker)
                            .name("Asset-" + ticker)
                            .currentPrice(priceValue)
                            .dividendYield(divValue)
                            .pVp(pvpValue)
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return new GroupSetWithFailures(
                            allGroups, goodGroups, failingGroups, List.of(asset), userId);
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

    record RuleSetWithFailures(
            List<Rule> allRules,
            List<Rule> goodRules,
            List<Rule> failingRules,
            List<Asset> assets,
            long userId) {}

    record GroupSetWithFailures(
            List<RuleGroup> allGroups,
            List<RuleGroup> goodGroups,
            List<RuleGroup> failingGroups,
            List<Asset> assets,
            long userId) {}

    record RuleSpec(RuleField field, ComparisonOperator operator, BigDecimal offset) {}
}
