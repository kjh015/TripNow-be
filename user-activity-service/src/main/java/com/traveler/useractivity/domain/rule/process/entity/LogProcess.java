package com.traveler.useractivity.domain.rule.process.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
@Table(
        name = "log_process",
        indexes = {@Index(name = "idx_log_process_deleted_at_status", columnList = "is_deleted, deleted_at")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_log_process_name", columnNames = "name")})
public class LogProcess extends BaseEntity {
    // name은 파이프라인이 X-Log-Process-Code 헤더로 프로세스를 찾는 코드로 쓰인다

    private String name;
    private String description;

    @Builder.Default
    private boolean isDeleted = false;

    private Instant deletedAt;

    public void delete() {
        if (this.isDeleted) {
            return;
        }
        this.isDeleted = true;
        this.deletedAt = Instant.now();

        // Unique 제약 조건 충돌 방지 (name)
        // 삭제한 프로세스의 이름으로 다시 만들 수 있도록 처리
        this.name = this.name + "_del_" + this.deletedAt.toEpochMilli();
    }

    public void update(String name, String description) {
        validateNotDeleted();
        this.name = name;
        this.description = description;
    }

    private void validateNotDeleted() {
        if (this.isDeleted) {
            throw new UserActivityServiceException(UserActivityServiceErrorCode.LOG_PROCESS_ALREADY_DELETED);
        }
    }
}
