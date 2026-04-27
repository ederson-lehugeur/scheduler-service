package com.invest.adapters.scheduler;

import com.invest.domain.ports.in.EvaluateRulesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Slf4j
public class RuleEvaluationJob implements Job {

    @Autowired
    private EvaluateRulesUseCase evaluateRulesUseCase;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            log.info("Starting rule evaluation job, correlationId={}", correlationId);
            evaluateRulesUseCase.execute();
            log.info("Rule evaluation job completed successfully, correlationId={}", correlationId);
        } catch (Exception exception) {
            log.error("Error during rule evaluation job execution, correlationId={}", correlationId, exception);
            throw new JobExecutionException(exception);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
