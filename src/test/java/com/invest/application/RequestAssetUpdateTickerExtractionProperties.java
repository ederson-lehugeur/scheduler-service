package com.invest.application;

import com.invest.application.usecases.RequestAssetUpdateUseCaseImpl;
import com.invest.domain.entities.ComparisonOperator;
import com.invest.domain.entities.Rule;
import com.invest.domain.entities.RuleField;
import com.invest.domain.entities.RuleGroup;
import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 3: Extracao de tickers unicos e construcao do evento.
 *
 * For any set of active individual rules (with groupId == null) and rule groups,
 * the published UpdateAssetsEvent must contain exactly the union set of tickers
 * from both sources, without duplicates. If the ticker set is empty, no event
 * should be published.
 *
 * Validates: Requirements 3.3, 3.4, 4.4
 */
class RequestAssetUpdateTickerExtractionProperties {

    @Property
    void publishedEventContainsExactlyUnionOfUniqueTickers(
            @ForAll("individualRulesAndGroups") RulesAndGroups rulesAndGroups) {

        List<Rule> allRules = rulesAndGroups.allRules();
        List<Rule> individualRules = allRules.stream()
                .filter(r -> r.getGroupId() == null)
                .toList();
        List<RuleGroup> groups = rulesAndGroups.groups();

        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetUpdateEventPublisher publisher = mock(AssetUpdateEventPublisher.class);

        RequestAssetUpdateUseCaseImpl useCase = new RequestAssetUpdateUseCaseImpl(
                ruleRepository, ruleGroupRepository, publisher);

        when(ruleRepository.findAllActive()).thenReturn(allRules);
        when(ruleGroupRepository.findAllWithRules()).thenReturn(groups);

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        Set<String> expectedTickers = Stream.concat(
                individualRules.stream().map(Rule::getTicker),
                groups.stream().map(RuleGroup::getTicker)
        ).collect(Collectors.toSet());

        if (expectedTickers.isEmpty()) {
            verify(publisher, never()).publish(any());
        } else {
            ArgumentCaptor<UpdateAssetsEvent> captor = ArgumentCaptor.forClass(UpdateAssetsEvent.class);
            verify(publisher).publish(captor.capture());
            UpdateAssetsEvent event = captor.getValue();

            assertThat(event.eventType()).isEqualTo("UPDATE_ASSETS");
            assertThat(new HashSet<>(event.data().assets())).isEqualTo(expectedTickers);
            assertThat(event.data().assets()).doesNotHaveDuplicates();
        }
    }

    @Property
    void noEventPublishedWhenTickerSetIsEmpty() {
        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetUpdateEventPublisher publisher = mock(AssetUpdateEventPublisher.class);

        RequestAssetUpdateUseCaseImpl useCase = new RequestAssetUpdateUseCaseImpl(
                ruleRepository, ruleGroupRepository, publisher);

        when(ruleRepository.findAllActive()).thenReturn(List.of());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        verify(publisher, never()).publish(any());
    }

    @Provide
    Arbitrary<RulesAndGroups> individualRulesAndGroups() {
        Arbitrary<String> tickers = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(6)
                .map(String::toUpperCase);

        Arbitrary<List<String>> tickerPool = tickers.list().ofMinSize(1).ofMaxSize(5);

        return tickerPool.flatMap(pool -> {
            Arbitrary<List<Rule>> individualRules = Arbitraries.of(pool)
                    .list().ofMinSize(0).ofMaxSize(5)
                    .map(selectedTickers -> selectedTickers.stream()
                            .map(t -> buildRule(t, null))
                            .toList());

            Arbitrary<List<Rule>> groupedRules = Arbitraries.of(pool)
                    .list().ofMinSize(0).ofMaxSize(3)
                    .map(selectedTickers -> selectedTickers.stream()
                            .map(t -> buildRule(t, 100L))
                            .toList());

            Arbitrary<List<RuleGroup>> groups = Arbitraries.of(pool)
                    .list().ofMinSize(0).ofMaxSize(3)
                    .map(selectedTickers -> {
                        List<RuleGroup> result = new ArrayList<>();
                        long groupId = 100L;
                        for (String ticker : selectedTickers) {
                            result.add(buildGroup(groupId++, ticker));
                        }
                        return result;
                    });

            return Combinators.combine(individualRules, groupedRules, groups)
                    .as((indiv, grouped, grps) -> {
                        List<Rule> allRules = new ArrayList<>();
                        allRules.addAll(indiv);
                        allRules.addAll(grouped);
                        return new RulesAndGroups(allRules, grps);
                    });
        });
    }

    private Rule buildRule(String ticker, Long groupId) {
        return Rule.builder()
                .id((long) (Math.random() * 100_000))
                .userId(1L)
                .ticker(ticker)
                .groupId(groupId)
                .field(RuleField.PRICE)
                .operator(ComparisonOperator.GREATER_THAN)
                .targetValue(BigDecimal.TEN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private RuleGroup buildGroup(long groupId, String ticker) {
        Rule rule = buildRule(ticker, groupId);
        return RuleGroup.builder()
                .id(groupId)
                .userId(1L)
                .ticker(ticker)
                .name("Group-" + groupId)
                .rules(List.of(rule))
                .createdAt(LocalDateTime.now())
                .build();
    }

    record RulesAndGroups(List<Rule> allRules, List<RuleGroup> groups) {}
}
