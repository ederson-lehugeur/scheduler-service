package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.EvaluateRulesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates: Requirements 5.1, 5.4
 */
@ExtendWith(MockitoExtension.class)
class RuleEvaluationJobTest {

    @Mock
    private EvaluateRulesUseCase evaluateRulesUseCase;

    @Mock
    private JobExecutionContext jobExecutionContext;

    private RuleEvaluationJob ruleEvaluationJob;

    @BeforeEach
    void setUp() {
        MDC.clear();
        ruleEvaluationJob = new RuleEvaluationJob();
        ReflectionTestUtils.setField(ruleEvaluationJob, "evaluateRulesUseCase", evaluateRulesUseCase);
    }

    @Test
    void shouldSetCorrelationIdInMdcBeforeUseCaseExecution() throws JobExecutionException {
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedCorrelationId.set(MDC.get("correlationId"));
            return null;
        }).when(evaluateRulesUseCase).execute();

        ruleEvaluationJob.execute(jobExecutionContext);

        String correlationId = capturedCorrelationId.get();
        assertNotNull(correlationId, "correlationId should be set in MDC during use case execution");
        assertDoesNotThrow(() -> UUID.fromString(correlationId), "correlationId should be a valid UUID");
    }

    @Test
    void shouldClearCorrelationIdFromMdcAfterSuccessfulExecution() throws JobExecutionException {
        ruleEvaluationJob.execute(jobExecutionContext);

        assertNull(MDC.get("correlationId"), "correlationId should be cleared from MDC after successful execution");
    }

    @Test
    void shouldClearCorrelationIdFromMdcAfterFailedExecution() {
        doThrow(new RuntimeException("evaluation failure")).when(evaluateRulesUseCase).execute();

        assertThrows(JobExecutionException.class, () -> ruleEvaluationJob.execute(jobExecutionContext));

        assertNull(MDC.get("correlationId"), "correlationId should be cleared from MDC after failed execution");
    }

    @Test
    void shouldCallUseCaseExactlyOnce() throws JobExecutionException {
        ruleEvaluationJob.execute(jobExecutionContext);

        verify(evaluateRulesUseCase, times(1)).execute();
    }
}
