package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
import net.jqwik.api.*;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 1: MDC cleanup apos qualquer resultado de execucao.
 *
 * For any execution result of AssetUpdateRequestJob (success or exception thrown
 * by the use case), the MDC must not contain the key "correlationId" after the
 * execute method completes.
 *
 * Validates: Requirements 1.4
 */
class AssetUpdateRequestJobMdcCleanupProperties {

    @Property
    void mdcIsClearedAfterSuccessfulExecution(@ForAll("successExecutions") boolean success) throws Exception {
        RequestAssetUpdateUseCase useCase = mock(RequestAssetUpdateUseCase.class);
        JobExecutionContext context = mock(JobExecutionContext.class);

        AssetUpdateRequestJob job = new AssetUpdateRequestJob();
        ReflectionTestUtils.setField(job, "requestAssetUpdateUseCase", useCase);

        MDC.clear();

        try {
            job.execute(context);
        } catch (JobExecutionException ignored) {
            // expected for failure cases
        }

        assertThat(MDC.get("correlationId"))
                .as("correlationId must be removed from MDC after successful execution")
                .isNull();
    }

    @Property
    void mdcIsClearedAfterFailedExecution(@ForAll("randomExceptions") RuntimeException exception) {
        RequestAssetUpdateUseCase useCase = mock(RequestAssetUpdateUseCase.class);
        JobExecutionContext context = mock(JobExecutionContext.class);

        doThrow(exception).when(useCase).execute();

        AssetUpdateRequestJob job = new AssetUpdateRequestJob();
        ReflectionTestUtils.setField(job, "requestAssetUpdateUseCase", useCase);

        MDC.clear();

        try {
            job.execute(context);
        } catch (JobExecutionException ignored) {
            // expected
        }

        assertThat(MDC.get("correlationId"))
                .as("correlationId must be removed from MDC after failed execution")
                .isNull();
    }

    @Provide
    Arbitrary<Boolean> successExecutions() {
        return Arbitraries.just(true);
    }

    @Provide
    Arbitrary<RuntimeException> randomExceptions() {
        Arbitrary<String> messages = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        return messages.map(RuntimeException::new);
    }
}
