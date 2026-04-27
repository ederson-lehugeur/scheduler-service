package com.invest.adapters.persistence;

import com.invest.domain.entities.RuleGroup;
import com.invest.domain.ports.out.RuleGroupRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RuleGroupRepositoryAdapter implements RuleGroupRepository {

    private final JpaRuleGroupRepository jpaRepository;

    public RuleGroupRepositoryAdapter(JpaRuleGroupRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<RuleGroup> findAllWithRules() {
        return jpaRepository.findAllWithRules().stream()
                .map(RuleGroupMapper::toDomain)
                .toList();
    }
}
