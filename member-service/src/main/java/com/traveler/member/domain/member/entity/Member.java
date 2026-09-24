package com.traveler.member.domain.member.entity;

import com.traveler.common.db.entity.BaseEntity;
import com.traveler.member.domain.member.enums.Gender;
import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

@SuperBuilder
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member",
        indexes = {@Index(name = "idx_member_deleted_at_status", columnList = "is_deleted, deleted_at")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_member_provider_provider_id",
                    columnNames = {"provider", "provider_id"})
        })
public class Member extends BaseEntity {

    @Column(unique = true, length = 50)
    private String loginId;

    private String password;

    private String email;

    @Column(length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Gender gender = Gender.NONE;

    private LocalDate birthDate;

    private String providerId;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MemberRole> roles = new ArrayList<>();

    @Builder.Default
    private boolean isDeleted = false;

    private Instant deletedAt;

    public void delete() {
        if (this.isDeleted) {
            return;
        }
        this.isDeleted = true;
        this.deletedAt = Instant.now();

        // Unique 제약 조건 충돌 방지 (loginId, email, providerId)
        // 탈퇴한 회원의 아이디나 이메일로 다른/같은 사용자가 재가입할 수 있도록 처리
        String deleteSuffix = "_del_" + this.deletedAt.toEpochMilli();

        if (this.loginId != null) {
            int maxBaseLength = 50 - deleteSuffix.length();
            String base =
                    this.loginId.length() > maxBaseLength ? this.loginId.substring(0, maxBaseLength) : this.loginId;
            this.loginId = base + deleteSuffix;
        }
        if (this.email != null) {
            this.email = this.email + deleteSuffix;
        }
        if (this.providerId != null) {
            this.providerId = this.providerId + deleteSuffix;
        }
    }

    public void update(String nickname, Gender gender, LocalDate birthDate) {
        validateNotDeleted();
        this.nickname = nickname;
        this.gender = gender;
        this.birthDate = birthDate;
    }

    public void updatePassword(String password) {
        validateNotDeleted();
        this.password = password;
    }

    public Integer getAge() {
        if (this.birthDate == null) {
            return null;
        }
        return (int) ChronoUnit.YEARS.between(this.birthDate, LocalDate.now());
    }

    public List<RoleType> getRoleTypes() {
        return this.roles.stream().map(MemberRole::getRoleType).toList();
    }

    public void addRole(RoleType roleType) {
        validateNotDeleted();

        // 이미 해당 권한을 가지고 있는지 검증
        boolean hasRole = this.roles.stream().anyMatch(role -> role.getRoleType() == roleType);

        if (!hasRole) {
            MemberRole newRole =
                    MemberRole.builder().member(this).roleType(roleType).build();
            this.roles.add(newRole);
        }
    }

    private void validateNotDeleted() {
        if (this.isDeleted) {
            throw new MemberServiceException(MemberServiceErrorCode.MEMBER_ALREADY_DELETED);
        }
    }
}
