package com.invest.infrastructure.config;

import com.invest.domain.ports.in.EvaluateRulesUseCase;
import com.invest.domain.ports.out.AlertRepository;
import com.invest.domain.ports.out.AssetRepository;
import com.invest.domain.ports.out.EventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import com.invest.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Validates: Requirements 1.2, 1.4
 *
 * Verifies the Spring context loads successfully with all required beans
 * for the scheduler-service after monolith code removal.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigurationSmokeTest {

    @Autowired
    private EvaluateRulesUseCase evaluateRulesUseCase;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RuleGroupRepository ruleGroupRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Test
    void contextLoads() {
        // Spring context loaded without errors - implicit assertion
    }

    @Test
    void allRequiredBeansArePresent() {
        assertNotNull(evaluateRulesUseCase, "EvaluateRulesUseCase bean must be present");
        assertNotNull(ruleRepository, "RuleRepository bean must be present");
        assertNotNull(ruleGroupRepository, "RuleGroupRepository bean must be present");
        assertNotNull(assetRepository, "AssetRepository bean must be present");
        assertNotNull(alertRepository, "AlertRepository bean must be present");
        assertNotNull(userRepository, "UserRepository bean must be present");
        assertNotNull(eventPublisher, "EventPublisher bean must be present");
    }
}
