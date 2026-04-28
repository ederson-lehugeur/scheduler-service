package com.invest.application.usecases;

import com.invest.domain.entities.ComparisonOperator;
import com.invest.domain.entities.Rule;
import com.invest.domain.entities.RuleField;
import com.invest.domain.entities.RuleGroup;
import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestAssetUpdateUseCaseImplTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleGroupRepository ruleGroupRepository;

    @Mock
    private AssetUpdateEventPublisher assetUpdateEventPublisher;

    private RequestAssetUpdateUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestAssetUpdateUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetUpdateEventPublisher);
    }

    @Test
    void shouldNotPublishEvent_whenNoActiveRulesOrGroupsExist() {
        when(ruleRepository.findAllActive()).thenReturn(List.of());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        useCase.execute();

        verify(assetUpdateEventPublisher, never()).publish(any());
    }

    @Test
    void shouldCallBothRepositories() {
        when(ruleRepository.findAllActive()).thenReturn(List.of());
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        useCase.execute();

        verify(ruleRepository).findAllActive();
        verify(ruleGroupRepository).findAllWithRules();
    }

    @Test
    void shouldFilterOutRulesWithGroupId() {
        Rule individualRule = buildRule(1L, "PETR4", null);
        Rule groupedRule = buildRule(2L, "VALE3", 100L);

        when(ruleRepository.findAllActive()).thenReturn(List.of(individualRule, groupedRule));
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        ArgumentCaptor<UpdateAssetsEvent> captor = ArgumentCaptor.forClass(UpdateAssetsEvent.class);
        verify(assetUpdateEventPublisher).publish(captor.capture());
        UpdateAssetsEvent event = captor.getValue();

        assertThat(event.data().assets()).containsExactly("PETR4");
    }

    @Test
    void shouldPublishEventWithTickersFromBothIndividualRulesAndGroups() {
        Rule individualRule = buildRule(1L, "PETR4", null);
        RuleGroup group = buildGroup(100L, "VALE3");

        when(ruleRepository.findAllActive()).thenReturn(List.of(individualRule));
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of(group));

        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        ArgumentCaptor<UpdateAssetsEvent> captor = ArgumentCaptor.forClass(UpdateAssetsEvent.class);
        verify(assetUpdateEventPublisher).publish(captor.capture());
        UpdateAssetsEvent event = captor.getValue();

        assertThat(event.data().assets()).containsExactlyInAnyOrder("PETR4", "VALE3");
        assertThat(event.eventType()).isEqualTo("UPDATE_ASSETS");
    }

    @Test
    void shouldNotPublishEvent_whenOnlyGroupedRulesExistWithoutGroups() {
        Rule groupedRule = buildRule(1L, "VALE3", 100L);

        when(ruleRepository.findAllActive()).thenReturn(List.of(groupedRule));
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        useCase.execute();

        verify(assetUpdateEventPublisher, never()).publish(any());
    }

    private Rule buildRule(Long id, String ticker, Long groupId) {
        return Rule.builder()
                .id(id)
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

    private RuleGroup buildGroup(Long groupId, String ticker) {
        Rule rule = buildRule(1L, ticker, groupId);
        return RuleGroup.builder()
                .id(groupId)
                .userId(1L)
                .ticker(ticker)
                .name("Group-" + groupId)
                .rules(List.of(rule))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
