package com.traveler.post.integration.useractivity.dto.message;

/** user-activity-service가 발행하는 이벤트(user-activity-service.sink.events)의 payload. */
public final class UserActivityMessage {

    private UserActivityMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record PostViewedDTO(Long postId) {}
}
