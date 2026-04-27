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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 6: Batch asset loading collects all unique tickers.
 *
 * For any set of active individual rules and rule groups, the EvaluateRulesUseCaseImpl
 * calls assetRepository.findByTickers exactly once with a set containing every unique
 * ticker referenced by the rules and groups.
 *
 * Validates: Requirements 2.2, 2.3
 */
class EvaluateRulesBatchLoadingProperties {

    @SuppressWarnings("unchecked")
    @Property
    void findByTickersCalledOnceWithAllUniqueTickers(
            @ForAll("rulesAndGroups") RulesAndGroups input) {

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        AlertRepository alertRepository = mock(AlertRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);

        EvaluateRulesUseCaseImpl useCase = new EvaluateRulesUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);

        // findAllActive returns all rules (both individual and grouped)
        // The use case filters individual rules (groupId == null) internally
        List<Rule> allRules = new ArrayList<>(input.individualRules());
        for (RuleGroup group : input.groups()) {
            allRules.addAll(group.getRules());
        }

        when(ruleRepository.findAllActive()).thenReturn(allRules);
        when(ruleGroupRepository.findAllWithRules()).thenReturn(input.groups());
        when(assetRepository.findByTickers(anySet())).thenReturn(input.assets());

        // Stub alert checks to return true (no alerts triggered) - we only care about the batch load
        when(alertRepository.existsActiveAlert(anyLong(), anyString())).thenReturn(true);
        when(alertRepository.existsActiveAlertForGroup(anyLong(), anyString())).thenReturn(true);

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        // Compute expected tickers: all unique tickers from individual rules and groups
        Set<String> expectedTickers = Stream.concat(
                input.individualRules().stream().map(Rule::getTicker),
                input.groups().stream().map(RuleGroup::getTicker)
        ).collect(Collectors.toSet());

        if (expectedTickers.isEmpty()) {
            // When no rules or groups exist, findByTickers should not be called
            verify(assetRepository, never()).findByTickers(anySet());
        } else {
            // findByTickers called exactly once
            ArgumentCaptor<Set<String>> tickerCaptor = ArgumentCaptor.forClass(Set.class);
            verify(assetRepository, times(1)).findByTickers(tickerCaptor.capture());

            Set<String> actualTickers = tickerCaptor.getValue();
            assertThat(actualTickers).isEqualTo(expectedTickers);
        }
    }

    @Provide
    Arbitrary<RulesAndGroups> rulesAndGroups() {
        Arbitrary<Integer> individualCounts = Arbitraries.integers().between(1, 5);
        Arbitrary<Integer> groupCounts = Arbitraries.integers().between(1, 3);
        Arbitrary<Long> userIds = Arbitraries.longs().between(1, 100_000);

        // Ticker pool: 3-6 tickers to ensure some overlap between rules and groups
        Arbitrary<List<String>> tickerPools = Arbitraries.strings().alpha().ofLength(4)
                .map(String::toUpperCase)
                .list().ofMinSize(3).ofMaxSize(6).uniqueElements();

        return Combinators.combine(individualCounts, groupCounts, userIds, tickerPools)
                .flatAs((indCount, grpCount, userId, tickerPool) ->
                        generateRulesAndGroups(indCount, grpCount, userId, tickerPool));
    }

    private Arbitrary<RulesAndGroups> generateRulesAndGroups(
            int individualCount, int groupCount, long userId, List<String> tickerPool) {

        Arbitrary<RuleField> fields = Arbitraries.of(RuleField.values());
        Arbitrary<ComparisonOperator> operators = Arbitraries.of(ComparisonOperator.values());
        Arbitrary<BigDecimal> targetValues = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10_000))
                .ofScale(2);

        // Generate ticker indices to pick from the pool (allows overlap)
        Arbitrary<List<Integer>> indTickerIndices = Arbitraries.integers()
                .between(0, tickerPool.size() - 1)
                .list().ofSize(individualCount);
        Arbitrary<List<Integer>> grpTickerIndices = Arbitraries.integers()
                .between(0, tickerPool.size() - 1)
                .list().ofSize(groupCount);
        Arbitrary<Integer> ruleCounts = Arbitraries.integers().between(1, 3);

        return Combinators.combine(indTickerIndices, grpTickerIndices, fields, operators, targetValues, ruleCounts)
                .as((indIndices, grpIndices, field, operator, targetValue, ruleCount) -> {
                    List<Rule> individualRules = new ArrayList<>();
                    Set<String> allTickers = new HashSet<>();

                    // Build individual rules
                    for (int i = 0; i < indIndices.size(); i++) {
                        String ticker = tickerPool.get(indIndices.get(i));
                        allTickers.add(ticker);

                        individualRules.add(Rule.builder()
                                .id((long) (i + 1))
                                .userId(userId)
                                .ticker(ticker)
                                .groupId(null)
                                .field(field)
                                .operator(operator)
                                .targetValue(targetValue)
                                .active(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());
                    }

                    // Build rule groups
                    List<RuleGroup> groups = new ArrayList<>();
                    for (int g = 0; g < grpIndices.size(); g++) {
                        String ticker = tickerPool.get(grpIndices.get(g));
                        allTickers.add(ticker);
                        long groupId = g + 1000L;

                        List<Rule> groupRules = new ArrayList<>();
                        for (int r = 0; r < ruleCount; r++) {
                            groupRules.add(Rule.builder()
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

                        groups.add(RuleGroup.builder()
                                .id(groupId)
                                .userId(userId)
                                .ticker(ticker)
                                .name("Group-" + groupId)
                                .rules(groupRules)
                                .createdAt(LocalDateTime.now())
                                .build());
                    }

                    // Build assets for all unique tickers
                    List<Asset> assets = allTickers.stream()
                            .map(ticker -> Asset.builder()
                                    .id((long) ticker.hashCode())
                                    .ticker(ticker)
                                    .name("Asset-" + ticker)
                                    .currentPrice(BigDecimal.valueOf(100))
                                    .dividendYield(BigDecimal.valueOf(5))
                                    .pVp(BigDecimal.ONE)
                                    .updatedAt(LocalDateTime.now())
                                    .build())
                            .toList();

                    return new RulesAndGroups(individualRules, groups, assets);
                });
    }

    record RulesAndGroups(List<Rule> individualRules, List<RuleGroup> groups, List<Asset> assets) {}
}
