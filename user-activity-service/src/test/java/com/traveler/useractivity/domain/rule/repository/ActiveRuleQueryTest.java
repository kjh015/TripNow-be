package com.traveler.useractivity.domain.rule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.useractivity.domain.rule.dedup.entity.DedupRule;
import com.traveler.useractivity.domain.rule.dedup.repository.DedupRuleRepository;
import com.traveler.useractivity.domain.rule.filter.entity.FilterRule;
import com.traveler.useractivity.domain.rule.filter.repository.FilterRuleRepository;
import com.traveler.useractivity.domain.rule.format.entity.FormatRule;
import com.traveler.useractivity.domain.rule.format.repository.FormatRuleRepository;
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 운영은 MySQL이라 H2 MySQL 모드로 근사한다.
// *RuleProvider가 쓰는 조회 쿼리가 소프트 삭제된 프로세스의 규칙을 걸러내는지 확인한다.
// 규칙은 일부러 활성 상태로 남겨, 연쇄 삭제 이전에 쌓인 데이터처럼 프로세스만 삭제된 경우를 재현한다.
@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:active-rule-query;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database=h2",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "eureka.client.enabled=false"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ActiveRuleQueryTest.AuditingConfig.class)
class ActiveRuleQueryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}

    @Autowired
    private FormatRuleRepository formatRuleRepository;

    @Autowired
    private FilterRuleRepository filterRuleRepository;

    @Autowired
    private DedupRuleRepository dedupRuleRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("삭제되지 않은 프로세스의 활성 규칙은 조회된다")
    void activeRulesOfLiveProcessAreFound() {
        Long logProcessId = persistProcessWithActiveRules("live-process");

        assertActiveRuleCounts(logProcessId, 1);
    }

    @Test
    @DisplayName("소프트 삭제된 프로세스의 규칙은 활성 상태여도 조회되지 않는다")
    void activeRulesOfDeletedProcessAreNotFound() {
        Long logProcessId = persistProcessWithActiveRules("deleted-process");

        em.find(LogProcess.class, logProcessId).delete();
        em.flush();
        em.clear();

        assertActiveRuleCounts(logProcessId, 0);
    }

    private Long persistProcessWithActiveRules(String name) {
        LogProcess logProcess = LogProcess.builder().name(name).build();
        em.persist(logProcess);
        em.persist(FormatRule.builder()
                .logProcess(logProcess)
                .name("format")
                .isActive(true)
                .build());
        em.persist(FilterRule.builder()
                .logProcess(logProcess)
                .name("filter")
                .expression("true")
                .conditions(List.of())
                .isActive(true)
                .build());
        em.persist(DedupRule.builder()
                .logProcess(logProcess)
                .name("dedup")
                .rules(List.of())
                .isActive(true)
                .build());
        em.flush();
        em.clear();
        return logProcess.getId();
    }

    private void assertActiveRuleCounts(Long logProcessId, int expected) {
        assertThat(formatRuleRepository.findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(
                        logProcessId))
                .hasSize(expected);
        assertThat(filterRuleRepository.findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(
                        logProcessId))
                .hasSize(expected);
        assertThat(dedupRuleRepository.findAllByLogProcessIdAndLogProcessIsDeletedFalseAndIsActiveTrueOrderByIdAsc(
                        logProcessId))
                .hasSize(expected);
    }
}
