package com.traveler.useractivity.domain.rule.format.repository;

import com.traveler.useractivity.domain.rule.format.entity.FormatRule;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormatRuleRepository extends JpaRepository<FormatRule, Long> {
    Page<FormatRule> findByLogProcessId(Long logProcessId, Pageable pageable);

    List<FormatRule> findAllByLogProcessId(Long logProcessId);

    // 소프트 삭제된 프로세스의 규칙은 파이프라인에 적용하지 않는다
    List<FormatRule> findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(Long logProcessId);
}
