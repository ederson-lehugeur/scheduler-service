package com.invest.adapters.persistence;

import com.invest.domain.entities.Rule;
import com.invest.domain.ports.out.RuleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RuleRepositoryAdapter implements RuleRepository {

    private final JpaRuleRepository jpaRepository;

    public RuleRepositoryAdapter(JpaRuleRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Rule> findAllActive() {
        return jpaRepository.findAllActiveWithoutGroup().stream()
                .map(RuleMapper::toDomain)
                .toList();
    }

    @Override
    public List<Rule> findByGroupId(Long groupId) {
        return jpaRepository.findByGroupId(groupId).stream()
                .map(RuleMapper::toDomain)
                .toList();
    }
}
