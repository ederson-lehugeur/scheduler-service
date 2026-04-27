package com.invest.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JpaRuleRepository extends JpaRepository<RuleEntity, Long> {

    @Query("SELECT r FROM RuleEntity r JOIN FETCH r.asset WHERE r.active = true AND r.group IS NULL")
    List<RuleEntity> findAllActiveWithoutGroup();

    List<RuleEntity> findByGroupId(Long groupId);
}
