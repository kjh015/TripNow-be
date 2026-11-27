package com.traveler.web.global.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DownstreamCircuitBreakerConfig {

    /**
     * Feign 호출의 서킷 이름을 하위 서비스 이름(@FeignClient name)으로 정한다. 기본 resolver는 Feign 메서드마다 서킷을 만든다.
     * 이름이 yml instances에 없으면 default 설정의 서킷이 조용히 만들어지므로, 첫 호출에서 예외를 던져 드러낸다.
     */
    @Bean
    CircuitBreakerNameResolver circuitBreakerNameResolver(CircuitBreakerRegistry circuitBreakerRegistry) {
        return (feignClientName, target, method) -> {
            String service = target.name();
            if (circuitBreakerRegistry.find(service).isEmpty()) {
                throw new IllegalStateException(
                        "등록되지 않은 서킷 이름입니다. resilience4j.circuitbreaker.instances를 확인하세요. service: " + service);
            }
            return service;
        };
    }
}
