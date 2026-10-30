package com.traveler.useractivity.global.kafka;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated // 💡 기동 시점에 누락된 프로퍼티가 있는지 즉시 검증
@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicProperties(
        @NotBlank String formatStream,
        @NotBlank String filterStream,
        @NotBlank String dedupStream,
        @NotBlank String sinkStream,
        @NotBlank String sinkEvents) {}
