package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
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
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5
 */
@ExtendWith(MockitoExtension.class)
class AssetUpdateRequestJobTest {

    @Mock
    private RequestAssetUpdateUseCase requestAssetUpdateUseCase;

    @Mock
    private JobExecutionContext jobExecutionContext;

    private AssetUpdateRequestJob assetUpdateRequestJob;

    @BeforeEach
    void setUp() {
        MDC.clear();
        assetUpdateRequestJob = new AssetUpdateRequestJob();
        ReflectionTestUtils.setField(assetUpdateRequestJob, "requestAssetUpdateUseCase", requestAssetUpdateUseCase);
    }

    @Test
    void shouldSetCorrelationIdInMdcBeforeUseCaseExecution() throws JobExecutionException {
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        doAnswer(invocation -> {
            capturedCorrelationId.set(MDC.get("correlationId"));
            return null;
        }).when(requestAssetUpdateUseCase).execute();

        assetUpdateRequestJob.execute(jobExecutionContext);

        String correlationId = capturedCorrelationId.get();
        assertNotNull(correlationId, "correlationId should be set in MDC during use case execution");
        assertDoesNotThrow(() -> UUID.fromString(correlationId), "correlationId should be a valid UUID");
    }

    @Test
    void shouldClearCorrelationIdFromMdcAfterSuccessfulExecution() throws JobExecutionException {
        assetUpdateRequestJob.execute(jobExecutionContext);

        assertNull(MDC.get("correlationId"), "correlationId should be cleared from MDC after successful execution");
    }

    @Test
    void shouldClearCorrelationIdFromMdcAfterFailedExecution() {
        doThrow(new RuntimeException("update failure")).when(requestAssetUpdateUseCase).execute();

        assertThrows(JobExecutionException.class, () -> assetUpdateRequestJob.execute(jobExecutionContext));

        assertNull(MDC.get("correlationId"), "correlationId should be cleared from MDC after failed execution");
    }

    @Test
    void shouldCallUseCaseExactlyOnce() throws JobExecutionException {
        assetUpdateRequestJob.execute(jobExecutionContext);

        verify(requestAssetUpdateUseCase, times(1)).execute();
    }
}
