package com.invest.domain.ports.out;

import com.invest.domain.entities.Rule;

import java.util.List;

public interface RuleRepository {

    List<Rule> findAllActive();

    List<Rule> findByGroupId(Long groupId);
}
