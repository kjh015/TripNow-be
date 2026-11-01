package com.traveler.member.domain.member.repository;

import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberSearchRepository {
    // Admin
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.roles WHERE m.id = :id")
    Optional<Member> findByIdWithRoles(Long id);

    // User
    boolean existsByLoginIdAndIsDeletedFalse(String loginId);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByNicknameAndIsDeletedFalse(String nickname);

    Optional<Member> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.roles WHERE m.loginId = :loginId AND m.isDeleted = false")
    Optional<Member> findActiveByLoginIdWithRoles(String loginId);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.roles WHERE m.id = :id AND m.isDeleted = false")
    Optional<Member> findActiveByIdWithRoles(Long id);

    Optional<Member> findByProviderAndProviderIdAndIsDeletedFalse(Provider provider, String providerId);
}
