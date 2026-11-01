package com.traveler.member.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.traveler.member.domain.member.dto.request.AdminMemberRequest;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Provider;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.global.exception.MemberServiceException;
import com.traveler.member.global.exception.code.MemberServiceErrorCode;
import com.traveler.member.testsupport.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/** 컨테이너를 다른 통합 테스트와 공유하므로 데이터는 롤백하고, 조건마다 PREFIX 검색어로 이 테스트의 회원만 고른다. */
@Transactional
class MemberSearchRepositoryIntegrationTest extends IntegrationTestSupport {

    private static final String PREFIX = "kdsl";
    private static final Pageable PAGE = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "loginId"));

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    private Member alice;
    private Member bob;
    private Member carol;

    @BeforeEach
    void setUp() {
        alice = save(member("kdsl_alice", "앨리스", "alice@example.com"));
        alice.addRole(RoleType.ROLE_USER);
        alice.addRole(RoleType.ROLE_ADMIN);
        bob = save(Member.builder()
                .loginId("kdsl_bob")
                .nickname("kdsl닉")
                .email("bob@example.com")
                .provider(Provider.KAKAO)
                .providerId("kdsl-1")
                .build());
        bob.addRole(RoleType.ROLE_USER);
        carol = save(member("kdsl_carol", "캐럴", "kdsl.carol@example.com"));
        carol.delete();
        memberRepository.flush();

        // 한국 시간 2026-10-01 00:00, 2026-10-31 23:59, 2026-11-01 00:00
        setCreatedAt(alice, Instant.parse("2026-09-30T15:00:00Z"));
        setCreatedAt(bob, Instant.parse("2026-10-31T14:59:00Z"));
        setCreatedAt(carol, Instant.parse("2026-10-31T15:00:00Z"));
    }

    @Test
    @DisplayName("키워드는 loginId·nickname·email의 접두 일치로 찾는다")
    void search_keywordMatchesPrefixOfEachField() {
        assertThat(ids(condition("kdsl_a"))).containsExactly(alice.getId());
        assertThat(ids(condition("kdsl닉"))).containsExactly(bob.getId());
        assertThat(ids(condition("kdsl.carol"))).containsExactly(carol.getId());
        assertThat(ids(condition("alice"))).contains(alice.getId());
        assertThat(ids(condition("dsl_alice"))).isEmpty();
    }

    @Test
    @DisplayName("키워드의 LIKE 와일드카드는 글자 그대로 찾는다")
    void search_escapesLikeWildcards() {
        assertThat(ids(condition("kdsl%"))).isEmpty();
        assertThat(ids(condition("kdsl_a"))).containsExactly(alice.getId());
        assertThat(ids(condition("kdsl_lice"))).isEmpty();
    }

    @Test
    @DisplayName("빈 조건은 적용하지 않는다")
    void search_blankKeywordIsIgnored() {
        Page<Member> page = memberRepository.search(
                new AdminMemberRequest.SearchDTO("   ", null, null, null, null, null), PageRequest.of(0, 1000));

        assertThat(page.getContent()).extracting(Member::getId).contains(alice.getId(), bob.getId(), carol.getId());
    }

    @Test
    @DisplayName("가입 경로·권한·탈퇴 여부를 각각 거른다")
    void search_filtersByEachCondition() {
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, Provider.KAKAO, null, null, null, null)))
                .containsExactly(bob.getId());
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, RoleType.ROLE_ADMIN, null, null, null)))
                .containsExactly(alice.getId());
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, true, null, null)))
                .containsExactly(carol.getId());
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, false, null, null)))
                .containsExactly(alice.getId(), bob.getId());
    }

    @Test
    @DisplayName("가입일 기간에 날짜 범위의 양 끝을 넣어도 조회할 수 있다")
    void search_joinedDateAtExtremes() {
        List<Long> everyone = List.of(alice.getId(), bob.getId(), carol.getId());

        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, null, LocalDate.MAX)))
                .containsExactlyElementsOf(everyone);
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, null, LocalDate.MAX.minusDays(1))))
                .containsExactlyElementsOf(everyone);
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, null, LocalDate.of(9999, 12, 31))))
                .containsExactlyElementsOf(everyone);
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, LocalDate.MAX, null)))
                .isEmpty();
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, LocalDate.MIN, null)))
                .containsExactlyElementsOf(everyone);
    }

    @Test
    @DisplayName("가입일 기간은 한국 날짜 기준으로 양 끝을 포함한다")
    void search_joinedDateRangeIsInclusiveInKoreanDate() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);

        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, from, to)))
                .containsExactly(alice.getId(), bob.getId());
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, to.plusDays(1), null)))
                .containsExactly(carol.getId());
    }

    @Test
    @DisplayName("조건을 함께 주면 모두 만족하는 회원만 나온다")
    void search_combinesConditions() {
        assertThat(ids(new AdminMemberRequest.SearchDTO(
                        PREFIX, Provider.KAKAO, RoleType.ROLE_USER, false, LocalDate.of(2026, 10, 31), null)))
                .containsExactly(bob.getId());
        assertThat(ids(new AdminMemberRequest.SearchDTO(PREFIX, Provider.KAKAO, RoleType.ROLE_ADMIN, null, null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("권한이 여러 개여도 회원이 중복되지 않고 전체 건수가 조건과 일치한다")
    void search_totalMatchesConditionAcrossPages() {
        AdminMemberRequest.SearchDTO condition = new AdminMemberRequest.SearchDTO(PREFIX, null, null, null, null, null);

        Page<Member> first = memberRepository.search(condition, PageRequest.of(0, 2, Sort.by("loginId")));
        Page<Member> last = memberRepository.search(condition, PageRequest.of(1, 2, Sort.by("loginId")));

        assertThat(first.getContent()).extracting(Member::getId).containsExactly(alice.getId(), bob.getId());
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(last.getContent()).extracting(Member::getId).containsExactly(carol.getId());
        assertThat(last.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("허용한 필드로 정렬한다")
    void search_sortsByAllowedField() {
        Page<Member> page = memberRepository.search(
                condition(PREFIX), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent())
                .extracting(Member::getId)
                .containsExactly(carol.getId(), bob.getId(), alice.getId());
    }

    @Test
    @DisplayName("허용하지 않은 정렬 필드는 예외가 난다")
    void search_rejectsUnknownSortField() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> memberRepository.search(condition(PREFIX), pageable))
                .isInstanceOf(MemberServiceException.class)
                .hasFieldOrPropertyWithValue("code", MemberServiceErrorCode.MEMBER_INVALID_SORT);
    }

    private List<Long> ids(AdminMemberRequest.SearchDTO condition) {
        return memberRepository.search(condition, PAGE).getContent().stream()
                .map(Member::getId)
                .toList();
    }

    private AdminMemberRequest.SearchDTO condition(String keyword) {
        return new AdminMemberRequest.SearchDTO(keyword, null, null, null, null, null);
    }

    private Member member(String loginId, String nickname, String email) {
        return Member.builder().loginId(loginId).nickname(nickname).email(email).build();
    }

    private Member save(Member member) {
        return memberRepository.saveAndFlush(member);
    }

    // createdAt은 감사 기능이 채우므로 저장 후 직접 바꾼다. 앱과 같은 시간대 변환을 쓰도록 JDBC가 아니라 JPQL로 바꾼다
    private void setCreatedAt(Member member, Instant createdAt) {
        entityManager
                .createQuery("UPDATE Member m SET m.createdAt = :createdAt WHERE m.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", member.getId())
                .executeUpdate();
    }
}
