package com.traveler.web.domain.useractivity.client.dto.response;

import com.traveler.web.domain.useractivity.client.dto.DedupClientSpec;
import java.time.Instant;
import java.util.List;

public final class DedupRuleClientResponse {
    private DedupRuleClientResponse() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(Long dedupRuleId, Instant createdAt) {}

    public record UpdateDTO(Long dedupRuleId, Instant updatedAt) {}

    public record DeleteDTO(Long dedupRuleId, Instant deletedAt) {}

    public record ListDTO(Long dedupRuleId, String name, Instant createdAt, Instant updatedAt, boolean isActive) {}

    public record DetailDTO(Long dedupRuleId, String name, List<DedupClientSpec> rules, boolean isActive) {}
}
