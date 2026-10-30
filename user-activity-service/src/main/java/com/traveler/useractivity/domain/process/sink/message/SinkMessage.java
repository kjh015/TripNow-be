package com.traveler.useractivity.domain.process.sink.message;

public final class SinkMessage {

    private SinkMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // 조회수 증가용 메시지 포맷
    public record PostViewedDTO(Long postId) {}
}
