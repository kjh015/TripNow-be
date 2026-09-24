package com.traveler.post.domain.post.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostViewRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:trace:";
    private static final String VIEW_COUNT_BUFFER_KEY = "post:view_count:buffer";
    private static final String PROCESSING_KEY = "post:view_count:processing";
    private static final String FLUSH_LOCK_KEY = "post:view_count:flush_lock";

    /** 락 값이 자신이 설정한 토큰과 같을 때만 삭제해, 만료 후 다른 인스턴스가 잡은 락을 지우지 않습니다. */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    /**
     * 멱등성 키 선점(SET NX PX)과 조회수 증가(HINCRBY)를 하나의 Lua 스크립트로 원자 처리합니다.
     *
     * <p>SET NX 가 성공한 경우(=최초 처리)에만 HINCRBY 를 수행하고 1을 반환합니다.
     * 이미 키가 존재하면(=중복) 아무것도 하지 않고 0을 반환합니다.
     *
     * <p>두 연산이 서버 측에서 한 번에 실행되므로, 기존처럼 "키 선점 성공 후 증가 실패"로
     * 조회수가 영구 누락되는 구간이 존재하지 않습니다. 증가에 성공했을 때만 키가 남기 때문에,
     * Kafka 재시도/재전달이 일어나도 유실도 중복 카운트도 발생하지 않습니다.
     */
    private static final DefaultRedisScript<Long> INCREMENT_IF_FIRST_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
                redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
                return 1
            end
            return 0
            """,
            Long.class);

    /**
     * 최초 이벤트일 때만 조회수를 1 증가시키고 true 를 반환합니다. (중복이면 false)
     *
     * @param traceId 멱등성 판별용 traceId
     * @param postId  조회수를 증가시킬 게시글 ID
     * @param ttl     멱등성 키 TTL
     */
    public boolean incrementViewCountIfFirst(String traceId, Long postId, Duration ttl) {
        Long result = redisTemplate.execute(
                INCREMENT_IF_FIRST_SCRIPT,
                List.of(IDEMPOTENCY_PREFIX + traceId, VIEW_COUNT_BUFFER_KEY),
                String.valueOf(ttl.toMillis()),
                String.valueOf(postId));
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 조회수 동기화 락을 획득합니다(SET NX PX). 배포 중 post-service가 두 개 떠 있어도 한 곳에서만 동기화합니다.
     *
     * @return 획득에 성공하면 해제에 쓸 토큰, 실패하면 null
     */
    public String tryAcquireFlushLock(Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(FLUSH_LOCK_KEY, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * 자신이 획득한 조회수 동기화 락을 해제합니다.
     */
    public void releaseFlushLock(String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(FLUSH_LOCK_KEY), token);
    }

    /**
     * 처리용 키를 준비하고, 처리할 데이터가 있으면 true 를 반환합니다.
     *
     * <p>이전 주기에 실패해 남은 처리용 키가 있으면 새 버퍼를 옮기지 않고 그것부터 처리합니다.
     * RENAME 은 대상 키를 덮어써 남은 데이터를 유실시키므로 RENAMENX 로 옮깁니다.
     */
    public boolean prepareProcessingBuffer() {
        if (redisTemplate.hasKey(PROCESSING_KEY)) {
            return true;
        }
        if (!redisTemplate.hasKey(VIEW_COUNT_BUFFER_KEY)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.renameIfAbsent(VIEW_COUNT_BUFFER_KEY, PROCESSING_KEY));
    }

    /**
     * 처리용 키에 담긴 모든 조회수 데이터를 가져옵니다.
     */
    public Map<Object, Object> getProcessingViewCounts() {
        return redisTemplate.opsForHash().entries(PROCESSING_KEY);
    }

    /**
     * 처리가 완료된 버퍼 데이터를 삭제합니다.
     */
    public void deleteProcessingBuffer() {
        redisTemplate.delete(PROCESSING_KEY);
    }
}
