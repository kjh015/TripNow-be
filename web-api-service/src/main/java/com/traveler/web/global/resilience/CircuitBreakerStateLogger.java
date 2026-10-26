package com.traveler.web.global.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 서킷 상태가 바뀔 때(CLOSED → OPEN 등) WARN 로그를 남긴다. 호출마다는 남기지 않는다.
 *
 * <p>resilience4j-spring-boot3 자동 설정이 이 빈을 레지스트리에 등록하므로, yml에 정의한 인스턴스가 만들어질 때 리스너가 붙는다.
 */
@Component
@Slf4j
public class CircuitBreakerStateLogger implements RegistryEventConsumer<CircuitBreaker> {

    @Override
    public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
        CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "서킷 상태 변경. service: {}, {} -> {}, failureRate: {}%, slowCallRate: {}%",
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState(),
                        circuitBreaker.getMetrics().getFailureRate(),
                        circuitBreaker.getMetrics().getSlowCallRate()));
    }

    @Override
    public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {}

    @Override
    public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {}
}
