package com.invest.application;

import com.invest.application.usecases.RequestAssetUpdateUseCaseImpl;
import com.invest.domain.entities.ComparisonOperator;
import com.invest.domain.entities.Rule;
import com.invest.domain.entities.RuleField;
import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 4: CorrelationId do MDC propagado para o evento.
 *
 * For any UUID set in the MDC as correlationId before use case execution,
 * the correlationId field of the published UpdateAssetsEvent must equal the MDC value.
 *
 * Validates: Requirement 4.3
 */
class RequestAssetUpdateCorrelationIdProperties {

    @Property
    void correlationIdFromMdcIsPropagatedToEvent(@ForAll("uuids") String correlationId) {
        RuleRepository ruleRepository = mock(RuleRepository.class);
        RuleGroupRepository ruleGroupRepository = mock(RuleGroupRepository.class);
        AssetUpdateEventPublisher publisher = mock(AssetUpdateEventPublisher.class);

        RequestAssetUpdateUseCaseImpl useCase = new RequestAssetUpdateUseCaseImpl(
                ruleRepository, ruleGroupRepository, publisher);

        Rule rule = Rule.builder()
                .id(1L)
                .userId(1L)
                .ticker("PETR4")
                .groupId(null)
                .field(RuleField.PRICE)
                .operator(ComparisonOperator.GREATER_THAN)
                .targetValue(BigDecimal.TEN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(ruleRepository.findAllActive()).thenReturn(List.of(rule));
        when(ruleGroupRepository.findAllWithRules()).thenReturn(List.of());

        MDC.put("correlationId", correlationId);
        try {
            useCase.execute();
        } finally {
            MDC.remove("correlationId");
        }

        ArgumentCaptor<UpdateAssetsEvent> captor = ArgumentCaptor.forClass(UpdateAssetsEvent.class);
        verify(publisher).publish(captor.capture());
        UpdateAssetsEvent event = captor.getValue();

        assertThat(event.correlationId()).isEqualTo(correlationId);
    }

    @Provide
    Arbitrary<String> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}
