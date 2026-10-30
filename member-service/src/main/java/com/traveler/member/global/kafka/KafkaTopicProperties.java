package com.traveler.member.global.kafka;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicProperties(Map<String, String> topics) {
    public KafkaTopicProperties {
        topics = (topics == null) ? Map.of() : Map.copyOf(topics);
    }

    public String getTopic(String key) {
        return topics.get(key);
    }
}
