package com.invest.infrastructure.config;

import com.invest.adapters.scheduler.AssetUpdateRequestJob;
import com.invest.adapters.scheduler.RuleEvaluationJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

@Configuration
public class QuartzConfig {

    private final long evaluationIntervalMs;
    private final long assetUpdateDelayMs;

    public QuartzConfig(
            @Value("${app.scheduler.evaluation-interval-ms}") long evaluationIntervalMs,
            @Value("${app.scheduler.asset-update-delay-ms}") long assetUpdateDelayMs) {
        this.evaluationIntervalMs = evaluationIntervalMs;
        this.assetUpdateDelayMs = assetUpdateDelayMs;
    }

    @Bean
    public JobDetail assetUpdateRequestJobDetail() {
        return JobBuilder.newJob(AssetUpdateRequestJob.class)
                .withIdentity("assetUpdateRequestJob", "evaluation")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger assetUpdateRequestTrigger(JobDetail assetUpdateRequestJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(assetUpdateRequestJobDetail)
                .withIdentity("assetUpdateRequestTrigger", "evaluation")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(evaluationIntervalMs)
                        .repeatForever())
                .build();
    }

    @Bean
    public JobDetail ruleEvaluationJobDetail() {
        return JobBuilder.newJob(RuleEvaluationJob.class)
                .withIdentity("ruleEvaluationJob", "evaluation")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger ruleEvaluationTrigger(JobDetail ruleEvaluationJobDetail) {
        Date startTime = new Date(System.currentTimeMillis() + assetUpdateDelayMs);
        return TriggerBuilder.newTrigger()
                .forJob(ruleEvaluationJobDetail)
                .withIdentity("ruleEvaluationTrigger", "evaluation")
                .startAt(startTime)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(evaluationIntervalMs)
                        .repeatForever())
                .build();
    }
}
