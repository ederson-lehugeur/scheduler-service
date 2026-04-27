package com.invest.domain.ports.out;

import com.invest.domain.entities.RuleGroup;

import java.util.List;

public interface RuleGroupRepository {

    List<RuleGroup> findAllWithRules();
}
