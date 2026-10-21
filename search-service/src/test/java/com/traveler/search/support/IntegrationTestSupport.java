package com.traveler.search.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * Elasticsearch·Kafka·Redis 컨테이너를 띄우는 통합 테스트의 공통 설정.
 *
 * <p>컨테이너는 JVM당 한 번만 띄우고 모든 하위 테스트가 공유한다(같은 설정이면 Spring 컨텍스트도 캐시된다).
 * ElasticsearchConfig와 KafkaConfig가 클라이언트를 직접 만들어 {@code @ServiceConnection}의 연결 정보를 쓰지 않으므로,
 * ES·Kafka 주소는 {@code @DynamicPropertySource}로 넣는다.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.15.5";

    // 인덱스 설정의 korean_nori 분석기가 nori 플러그인을 요구한다 (운영 이미지와 같은 방식으로 설치)
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
                    DockerImageName.parse(new ImageFromDockerfile("tripnow-test/elasticsearch-nori:8.15.5", false)
                                    .withDockerfileFromBuilder(builder -> builder.from(ELASTICSEARCH_IMAGE)
                                            .run("elasticsearch-plugin install --batch analysis-nori")
                                            .build())
                                    .get())
                            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("cluster.routing.allocation.disk.threshold_enabled", "false");

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        Startables.deepStart(ELASTICSEARCH, KAFKA, REDIS).join();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // ElasticsearchConfig의 connectedTo()는 스킴 없는 host:port를 받는다
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
