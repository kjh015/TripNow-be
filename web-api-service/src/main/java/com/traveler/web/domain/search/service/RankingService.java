package com.traveler.web.domain.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traveler.web.domain.search.dto.response.RankingResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer container;
    private final Executor sseExecutor;
    private final ObjectMapper objectMapper;

    // 연결된 모든 클라이언트 저장소 (Thread-safe한 리스트 사용)
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    private static final String RANKING_CHANNEL = "ranking:live";
    private static final String RANKING_CACHE_KEY = "ranking:latest";

    private static final String EVENT_RANKING_UPDATE = "ranking-update";
    private static final String EVENT_CONNECTED = "connected";

    public int getActiveEmitterCount() {
        return emitters.size();
    }

    /**
     * 클라이언트가 SSE 연결을 시도할 때 호출
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분
        this.emitters.add(emitter);

        Runnable removeHandler = () -> {
            log.debug("Removing emitter: {}", emitter);
            this.emitters.remove(emitter);
        };

        // 연결 종료/타임아웃 시 리스트에서 제거
        emitter.onCompletion(removeHandler);
        emitter.onTimeout(removeHandler);
        emitter.onError((e) -> removeHandler.run());

        sendToClient(emitter, EVENT_CONNECTED, "SSE connected at " + LocalDateTime.now());

        // 초기 데이터 전송
        sseExecutor.execute(() -> {
            try {
                Object latest = redisTemplate.opsForValue().get(RANKING_CACHE_KEY);
                if (latest != null) {
                    RankingResponse.LiveDTO data = objectMapper.convertValue(latest, RankingResponse.LiveDTO.class);
                    sendToClient(emitter, EVENT_RANKING_UPDATE, data);
                }
            } catch (Exception e) {
                log.error("Initial data sync failed", e);
            }
        });

        return emitter;
    }

    /**
     * 주기적 Heartbeat 전송 (Nginx 연결 유지용)
     * 15초 주기로 모든 클라이언트에게 빈 데이터 전송
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            sseExecutor.execute(() -> send(emitter, SseEmitter.event().comment("heartbeat")));
        }
    }

    /**
     * Redis 채널을 구독하고 메시지가 오면 모든 클라이언트에게 브로드캐스트
     */
    @PostConstruct
    public void initRedisSubscription() {
        container.addMessageListener(
                (message, pattern) -> {
                    try {
                        RankingResponse.LiveDTO data =
                                objectMapper.readValue(message.getBody(), RankingResponse.LiveDTO.class);

                        log.debug("Ranking data deserialized explicitly. Broadcasting...");
                        broadcast(EVENT_RANKING_UPDATE, data);
                    } catch (IOException e) {
                        log.error("Failed to deserialize ranking data", e);
                    }
                },
                new ChannelTopic(RANKING_CHANNEL));
    }

    private void broadcast(String name, Object data) {
        for (SseEmitter emitter : emitters) {
            sseExecutor.execute(() -> sendToClient(emitter, name, data));
        }
    }

    private void sendToClient(SseEmitter emitter, String name, Object data) {
        send(emitter, SseEmitter.event().name(name).data(data));
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            // 연결이 이미 끊긴 경우(새로고침/탭 종료)는 정상 흐름이므로 debug로만 남긴다.
            // IOException 이후의 complete()/completeWithError() 호출은 컨테이너의 async 오류 처리와 중복되므로 호출하지 않는다.
            log.debug("Client disconnected, removing emitter: {}", e.getMessage());
            emitters.remove(emitter);
        } catch (IllegalStateException e) {
            // 직렬화 실패 등 예상치 못한 오류는 숨기지 않고 error로 남긴다.
            log.error("Unexpected SSE send failure", e);
            emitters.remove(emitter);
        }
    }
}
