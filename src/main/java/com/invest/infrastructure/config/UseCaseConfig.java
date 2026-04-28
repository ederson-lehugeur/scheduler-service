package com.invest.infrastructure.config;

import com.invest.application.usecases.EvaluateRulesUseCaseImpl;
import com.invest.application.usecases.RequestAssetUpdateUseCaseImpl;
import com.invest.domain.ports.in.EvaluateRulesUseCase;
import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
import com.invest.domain.ports.out.AlertRepository;
import com.invest.domain.ports.out.AssetRepository;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.domain.ports.out.EventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import com.invest.domain.ports.out.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public EvaluateRulesUseCase evaluateRulesUseCase(RuleRepository ruleRepository,
                                                     RuleGroupRepository ruleGroupRepository,
                                                     AssetRepository assetRepository,
                                                     AlertRepository alertRepository,
                                                     UserRepository userRepository,
                                                     EventPublisher eventPublisher) {
        return new EvaluateRulesUseCaseImpl(ruleRepository, ruleGroupRepository, assetRepository,
                alertRepository, userRepository, eventPublisher);
    }

    @Bean
    public RequestAssetUpdateUseCase requestAssetUpdateUseCase(
            RuleRepository ruleRepository,
            RuleGroupRepository ruleGroupRepository,
            AssetUpdateEventPublisher assetUpdateEventPublisher) {
        return new RequestAssetUpdateUseCaseImpl(
                ruleRepository, ruleGroupRepository, assetUpdateEventPublisher);
    }
}
