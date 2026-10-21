package com.traveler.member.testsupport.fixture;

import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.Provider;

/** member-service 테스트가 함께 쓰는 회원 데이터. */
public final class MemberFixture {

    /** 카카오 회원의 providerId */
    public static final String KAKAO_PROVIDER_ID = "1234567890";

    private MemberFixture() {}

    /** 일반 가입 회원. id는 원래 DB가 채우지만, Repository를 mock으로 두는 단위 테스트에서는 직접 넣는다. */
    public static Member member(Long id) {
        return Member.builder().id(id).loginId("traveler").nickname("여행자").build();
    }

    /** 카카오로 가입한 회원 */
    public static Member kakaoMember(Long id) {
        return Member.builder()
                .id(id)
                .provider(Provider.KAKAO)
                .providerId(KAKAO_PROVIDER_ID)
                .email("traveler@example.com")
                .build();
    }
}
