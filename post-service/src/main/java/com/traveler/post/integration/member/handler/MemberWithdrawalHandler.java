package com.traveler.post.integration.member.handler;

import com.traveler.post.domain.comment.service.CommentService;
import com.traveler.post.domain.like.service.LikeService;
import com.traveler.post.domain.post.service.PostService;
import com.traveler.post.integration.member.dto.event.MemberEvent;
import com.traveler.post.integration.member.mapper.MemberMapper;
import java.time.Instant;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴 Saga의 post-service 단계. 탈퇴한 회원의 게시글·댓글을 소프트 삭제하고 좋아요를 삭제한다.
 *
 * <p>회원 탈퇴(member-service)는 되돌릴 수 없는 단계(pivot transaction)이므로 이 단계에는 보상 트랜잭션이 없다. 대신 실패한 작업을
 * 재시도해 최종적으로 완료할 수 있도록 설계한다.
 *
 * <ul>
 *   <li>배치 단위 커밋: 각 배치를 별도 트랜잭션으로 처리한다. 데이터가 많아도 트랜잭션이 길어지지 않고, 중간에 실패해도 이미 커밋된
 *       배치는 유지된다.
 *   <li>멱등성: 이미 삭제된 행은 조회 대상에서 제외하고, 통계는 이번 배치에서 실제로 삭제한 행만큼만 차감한다. 같은 메시지를 다시
 *       처리해도 남은 데이터부터 이어서 삭제하며, 삭제할 데이터가 없으면 아무것도 변경하지 않는다. 따라서 별도의 event-id 처리 기록
 *       테이블이 필요하지 않다.
 *   <li>실패 처리: 예외를 그대로 전파해 리스너 컨테이너가 재시도하도록 한다. 재시도 횟수를 초과하면 DLT
 *       (member-service.member.events.DLT)로 보낸다(KafkaConfig).
 *   <li>완료 처리: 모든 배치가 끝나면 CONTENT_DELETED 이벤트를 발행해 member-service의 탈퇴 기록을 DONE으로 변경한다.
 *       같은 메시지를 다시 처리하면 이벤트가 중복 발행되지만, member-service는 상태가 PENDING인 경우에만 변경하므로
 *       최종 결과는 동일하다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberWithdrawalHandler {

    // 댓글·좋아요 배치는 게시글마다 통계 슬롯 하나를 커밋까지 잠그므로, 사용자 요청이 오래 기다리지 않게 크기를 작게 둔다
    static final int BATCH_SIZE = 100;

    private final PostService postService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final MemberMapper memberMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public void handle(Long memberId) {
        int posts = drain(() -> postService.deleteByMember(memberId, BATCH_SIZE));
        int comments = drain(() -> commentService.deleteByMember(memberId, BATCH_SIZE));
        int likes = drain(() -> likeService.removeByMember(memberId, BATCH_SIZE));
        log.info(
                "[MemberWithdrawal] 탈퇴 회원 콘텐츠 정리 memberId={} posts={} comments={} likes={}",
                memberId,
                posts,
                comments,
                likes);
        // Outbox 기록은 커밋 직전에 저장되므로 트랜잭션 안에서 발행한다(MemberEventListener)
        MemberEvent.ContentDeleted event =
                memberMapper.toContentDeletedEvent(memberId, Instant.now(), posts, comments, likes);
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    // 배치 하나(트랜잭션 하나)를 남은 행이 없을 때까지 반복한다. 조회한 행 수의 합을 돌려준다
    private static int drain(IntSupplier batch) {
        int total = 0;
        int found;
        do {
            found = batch.getAsInt();
            total += found;
        } while (found == BATCH_SIZE);
        return total;
    }
}
