package com.traveler.useractivity.domain.rule.dedup.repository;

import com.traveler.useractivity.domain.rule.dedup.entity.DedupRule;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DedupRuleRepository extends JpaRepository<DedupRule, Long> {
    Page<DedupRule> findByLogProcessId(Long logProcessId, Pageable pageable);

    List<DedupRule> findAllByLogProcessId(Long logProcessId);

    // 소프트 삭제된 프로세스의 규칙은 파이프라인에 적용하지 않는다
    List<DedupRule> findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(Long logProcessId);
}
