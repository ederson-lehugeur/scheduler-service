package com.invest.infrastructure.config;

import com.invest.adapters.scheduler.AssetUpdateRequestJob;
import com.invest.adapters.scheduler.RuleEvaluationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

import static org.junit.jupiter.api.Assertions.*;

class QuartzConfigTest {

    private static final long INTERVAL_MS = 60000L;
    private static final long DELAY_MS = 30000L;

    private QuartzConfig quartzConfig;

    @BeforeEach
    void setUp() {
        quartzConfig = new QuartzConfig(INTERVAL_MS, DELAY_MS);
    }

    @Test
    void shouldCreateJobDetailForRuleEvaluationJob() {
        JobDetail jobDetail = quartzConfig.ruleEvaluationJobDetail();

        assertNotNull(jobDetail);
        assertEquals(RuleEvaluationJob.class, jobDetail.getJobClass());
        assertEquals("ruleEvaluationJob", jobDetail.getKey().getName());
        assertEquals("evaluation", jobDetail.getKey().getGroup());
        assertTrue(jobDetail.isDurable());
    }

    @Test
    void shouldCreateTriggerWithConfiguredInterval() {
        JobDetail jobDetail = quartzConfig.ruleEvaluationJobDetail();
        Trigger trigger = quartzConfig.ruleEvaluationTrigger(jobDetail);

        assertNotNull(trigger);
        assertEquals("ruleEvaluationTrigger", trigger.getKey().getName());
        assertEquals("evaluation", trigger.getKey().getGroup());
        assertEquals(jobDetail.getKey(), trigger.getJobKey());
        assertInstanceOf(SimpleTrigger.class, trigger);

        SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
        assertEquals(INTERVAL_MS, simpleTrigger.getRepeatInterval());
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, simpleTrigger.getRepeatCount());
    }

    @Test
    void shouldAssociateTriggerWithJobDetail() {
        JobDetail jobDetail = quartzConfig.ruleEvaluationJobDetail();
        Trigger trigger = quartzConfig.ruleEvaluationTrigger(jobDetail);

        assertEquals(jobDetail.getKey(), trigger.getJobKey());
    }

    @Test
    void shouldCreateRuleEvaluationTriggerWithDelayedStart() {
        JobDetail jobDetail = quartzConfig.ruleEvaluationJobDetail();
        long beforeCreation = System.currentTimeMillis() + DELAY_MS;
        Trigger trigger = quartzConfig.ruleEvaluationTrigger(jobDetail);
        long afterCreation = System.currentTimeMillis() + DELAY_MS;

        assertNotNull(trigger.getStartTime());
        long startTime = trigger.getStartTime().getTime();
        assertTrue(startTime >= beforeCreation && startTime <= afterCreation,
                "Start time should be offset by the configured delay");
    }

    @Test
    void shouldCreateAssetUpdateRequestJobDetail() {
        JobDetail jobDetail = quartzConfig.assetUpdateRequestJobDetail();

        assertNotNull(jobDetail);
        assertEquals(AssetUpdateRequestJob.class, jobDetail.getJobClass());
        assertEquals("assetUpdateRequestJob", jobDetail.getKey().getName());
        assertEquals("evaluation", jobDetail.getKey().getGroup());
        assertTrue(jobDetail.isDurable());
    }

    @Test
    void shouldCreateAssetUpdateRequestTriggerWithConfiguredInterval() {
        JobDetail jobDetail = quartzConfig.assetUpdateRequestJobDetail();
        Trigger trigger = quartzConfig.assetUpdateRequestTrigger(jobDetail);

        assertNotNull(trigger);
        assertEquals("assetUpdateRequestTrigger", trigger.getKey().getName());
        assertEquals("evaluation", trigger.getKey().getGroup());
        assertEquals(jobDetail.getKey(), trigger.getJobKey());
        assertInstanceOf(SimpleTrigger.class, trigger);

        SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
        assertEquals(INTERVAL_MS, simpleTrigger.getRepeatInterval());
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, simpleTrigger.getRepeatCount());
    }

    @Test
    void shouldAssociateAssetUpdateTriggerWithJobDetail() {
        JobDetail jobDetail = quartzConfig.assetUpdateRequestJobDetail();
        Trigger trigger = quartzConfig.assetUpdateRequestTrigger(jobDetail);

        assertEquals(jobDetail.getKey(), trigger.getJobKey());
    }
}
