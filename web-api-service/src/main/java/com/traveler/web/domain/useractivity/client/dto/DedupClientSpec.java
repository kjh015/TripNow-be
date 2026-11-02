package com.traveler.web.domain.useractivity.client.dto;

import com.traveler.web.domain.useractivity.enums.MatchType;
import java.util.List;

public record DedupClientSpec(List<Condition> conditions, ExpirationTime expirationTime) {

    public record Condition(String field, String value, MatchType matchType) {}

    public record ExpirationTime(int days, int hours, int minutes, int seconds) {}
}
