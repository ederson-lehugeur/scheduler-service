package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Slf4j
public class AssetUpdateRequestJob implements Job {

    @Autowired
    private RequestAssetUpdateUseCase requestAssetUpdateUseCase;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            log.info("Starting asset update request job, correlationId={}", correlationId);
            requestAssetUpdateUseCase.execute();
            log.info("Asset update request job completed successfully, correlationId={}", correlationId);
        } catch (Exception exception) {
            log.error("Error during asset update request job execution, correlationId={}", correlationId, exception);
            throw new JobExecutionException(exception);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
