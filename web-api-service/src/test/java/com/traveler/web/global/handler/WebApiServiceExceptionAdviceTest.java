package com.traveler.web.global.handler;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class WebApiServiceExceptionAdviceTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TimeoutController())
            .setControllerAdvice(new WebApiServiceExceptionAdvice())
            .build();

    @Test
    @DisplayName("하위 서비스 타임아웃·연결 실패는 서킷이 열렸을 때와 같은 503 WEB503_1로 응답한다")
    void respondsServiceUnavailableOnRetryableException() throws Exception {
        mockMvc.perform(get("/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEB503_1"));
    }

    @RestController
    static class TimeoutController {

        @GetMapping("/timeout")
        String timeout() {
            throw mock(RetryableException.class);
        }
    }
}
