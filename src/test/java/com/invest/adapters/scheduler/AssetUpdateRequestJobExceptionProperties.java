package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
import net.jqwik.api.*;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

/**
 * Property 2: Excecoes propagadas como JobExecutionException.
 *
 * For any exception thrown by the RequestAssetUpdateUseCase during execution of
 * AssetUpdateRequestJob, the job must propagate the exception wrapped in a
 * JobExecutionException, and the cause of the JobExecutionException must be the
 * original exception.
 *
 * Validates: Requirements 1.5
 */
class AssetUpdateRequestJobExceptionProperties {

    @Property
    void exceptionIsWrappedInJobExecutionException(@ForAll("randomExceptions") RuntimeException originalException) {
        RequestAssetUpdateUseCase useCase = mock(RequestAssetUpdateUseCase.class);
        JobExecutionContext context = mock(JobExecutionContext.class);

        doThrow(originalException).when(useCase).execute();

        AssetUpdateRequestJob job = new AssetUpdateRequestJob();
        ReflectionTestUtils.setField(job, "requestAssetUpdateUseCase", useCase);

        MDC.clear();

        JobExecutionException thrown = catchThrowableOfType(
                () -> job.execute(context),
                JobExecutionException.class
        );

        assertThat(thrown)
                .as("Exception must be wrapped in JobExecutionException")
                .isNotNull();

        assertThat(thrown.getCause())
                .as("Cause of JobExecutionException must be the original exception")
                .isSameAs(originalException);
    }

    @Provide
    Arbitrary<RuntimeException> randomExceptions() {
        Arbitrary<String> messages = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        return Arbitraries.oneOf(
                messages.map(RuntimeException::new),
                messages.map(IllegalStateException::new),
                messages.map(IllegalArgumentException::new),
                messages.map(NullPointerException::new)
        );
    }
}
