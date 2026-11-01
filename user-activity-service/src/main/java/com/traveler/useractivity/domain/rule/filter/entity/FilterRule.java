package com.traveler.useractivity.domain.rule.filter.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.useractivity.domain.rule.filter.definition.FilterToken;
import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
@Table(
        name = "filter_rule",
        indexes = {@Index(name = "idx_filter_rule_deleted_at_status", columnList = "is_deleted, deleted_at")})
public class FilterRule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_process_id", nullable = false)
    private LogProcess logProcess;

    private String name;

    @Column(nullable = false, length = 1000)
    private String expression;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FilterToken> conditions;

    private boolean isActive;

    @Builder.Default
    private boolean isDeleted = false;

    private Instant deletedAt;

    public void delete() {
        if (this.isDeleted) {
            return;
        }
        this.isDeleted = true;
        this.deletedAt = Instant.now();
    }

    public void update(String name, String expression, List<FilterToken> conditions, boolean isActive) {
        validateNotDeleted();
        this.name = name;
        this.expression = expression;
        this.conditions = conditions;
        this.isActive = isActive;
    }

    private void validateNotDeleted() {
        if (this.isDeleted) {
            throw new UserActivityServiceException(UserActivityServiceErrorCode.FILTER_RULE_ALREADY_DELETED);
        }
    }
}
