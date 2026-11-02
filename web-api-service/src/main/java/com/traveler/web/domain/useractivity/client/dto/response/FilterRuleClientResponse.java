package com.traveler.web.domain.useractivity.client.dto.response;

import com.traveler.web.domain.useractivity.client.dto.FilterClientToken;
import java.time.Instant;
import java.util.List;

public final class FilterRuleClientResponse {
    private FilterRuleClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(Long filterRuleId, Instant createdAt) {}

    public record UpdateDTO(Long filterRuleId, Instant updatedAt) {}

    public record DeleteDTO(Long filterRuleId, Instant deletedAt) {}

    public record ListDTO(Long filterRuleId, String name, Instant createdAt, Instant updatedAt, boolean isActive) {}

    public record DetailDTO(
            Long filterRuleId, String name, String expression, List<FilterClientToken> conditions, boolean isActive) {}
}
