package com.traveler.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.traveler.web.testsupport.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class WebApiServiceApplicationTests extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {}

    @Test
    void usesCircuitBreakerFeignTargeter() {
        // 자동 설정 순서가 어긋나면 서킷 없이 기본 Targeter로 조용히 돈다. 전체 자동 설정에서 서킷 Targeter가 쓰이는지 본다
        assertThat(applicationContext.containsBean("circuitBreakerFeignTargeter"))
                .isTrue();
    }
}
