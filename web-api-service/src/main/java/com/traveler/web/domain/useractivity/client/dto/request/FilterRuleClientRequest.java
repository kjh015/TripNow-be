package com.traveler.web.domain.useractivity.client.dto.request;

import com.traveler.web.domain.useractivity.client.dto.FilterClientToken;
import java.util.List;

public final class FilterRuleClientRequest {
    private FilterRuleClientRequest() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record CreateDTO(String name, List<FilterClientToken> conditions, Boolean isActive) {}

    public record UpdateDTO(String name, List<FilterClientToken> conditions, Boolean isActive) {}
}
