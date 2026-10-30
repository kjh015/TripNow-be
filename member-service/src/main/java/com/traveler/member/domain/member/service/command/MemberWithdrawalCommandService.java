package com.traveler.member.domain.member.service.command;

import com.traveler.member.domain.member.repository.MemberWithdrawalRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴 Saga의 마지막 단계. post-service의 정리 완료를 받아 탈퇴 기록의 정리 상태를 DONE으로 바꾼다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalCommandService {

    private final MemberWithdrawalRepository memberWithdrawalRepository;

    @Transactional
    public void markContentDeleted(Long memberId, Instant deletedAt) {
        int updated = memberWithdrawalRepository.markContentDeleted(memberId, deletedAt);
        if (updated == 0) {
            // 같은 완료 메시지를 다시 받았거나, 탈퇴 기록 테이블이 생기기 전에 탈퇴한 회원이다
            log.info("[MemberContent] 정리 상태가 PENDING이 아니라 바꾸지 않음 memberId={}", memberId);
        }
    }
}
