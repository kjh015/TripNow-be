package com.traveler.useractivity.domain.rule.filter.repository;

import com.traveler.useractivity.domain.rule.filter.entity.FilterRule;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilterRuleRepository extends JpaRepository<FilterRule, Long> {
    Page<FilterRule> findByLogProcessId(Long logProcessId, Pageable pageable);

    List<FilterRule> findAllByLogProcessId(Long logProcessId);

    // 소프트 삭제된 프로세스의 규칙은 파이프라인에 적용하지 않는다
    List<FilterRule> findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(Long logProcessId);
}
