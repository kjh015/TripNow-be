package com.traveler.member.domain.auth.support;

import com.traveler.member.domain.auth.dto.request.AuthRequest;
import com.traveler.member.domain.auth.mapper.AuthMapper;
import com.traveler.member.domain.member.entity.Member;
import com.traveler.member.domain.member.enums.RoleType;
import com.traveler.member.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SocialMemberRegistrar {
    private final MemberRepository memberRepository;
    private final AuthMapper authMapper;

    // 별도 트랜잭션으로 저장: 중복 키 예외가 나도 호출한 쪽 트랜잭션은 롤백 대상이 되지 않음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member register(AuthRequest.OAuthLoginDTO dto) {
        Member newMember = authMapper.toCreateEntity(dto);
        newMember.addRole(RoleType.ROLE_USER);
        // 제약 위반을 이 트랜잭션 안에서 드러내기 위해 즉시 플러시
        return memberRepository.saveAndFlush(newMember);
    }
}
