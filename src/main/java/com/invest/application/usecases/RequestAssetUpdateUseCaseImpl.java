package com.invest.application.usecases;

import com.invest.domain.entities.Rule;
import com.invest.domain.entities.RuleGroup;
import com.invest.domain.events.UpdateAssetsEvent;
import com.invest.domain.ports.in.RequestAssetUpdateUseCase;
import com.invest.domain.ports.out.AssetUpdateEventPublisher;
import com.invest.domain.ports.out.RuleGroupRepository;
import com.invest.domain.ports.out.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class RequestAssetUpdateUseCaseImpl implements RequestAssetUpdateUseCase {

    private final RuleRepository ruleRepository;
    private final RuleGroupRepository ruleGroupRepository;
    private final AssetUpdateEventPublisher assetUpdateEventPublisher;

    @Override
    public void execute() {
        log.info("M=execute, I=Iniciando extracao de tickers para atualizacao de ativos");

        List<Rule> individualRules = ruleRepository.findAllActive().stream()
                .filter(rule -> rule.getGroupId() == null)
                .toList();

        List<RuleGroup> groups = ruleGroupRepository.findAllWithRules();

        Set<String> tickers = extractUniqueTickers(individualRules, groups);

        log.info("M=execute, I=Tickers unicos extraidos, tickerCount={}", tickers.size());

        if (tickers.isEmpty()) {
            log.info("M=execute, I=Nenhum ticker ativo encontrado, pulando publicacao de evento");
            return;
        }

        String correlationId = MDC.get("correlationId");

        UpdateAssetsEvent event = new UpdateAssetsEvent(
                "UPDATE_ASSETS",
                correlationId,
                new UpdateAssetsEvent.Data(List.copyOf(tickers))
        );

        assetUpdateEventPublisher.publish(event);
    }

    private Set<String> extractUniqueTickers(List<Rule> individualRules, List<RuleGroup> groups) {
        return Stream.concat(
                individualRules.stream().map(Rule::getTicker),
                groups.stream().map(RuleGroup::getTicker)
        ).collect(Collectors.toSet());
    }
}
