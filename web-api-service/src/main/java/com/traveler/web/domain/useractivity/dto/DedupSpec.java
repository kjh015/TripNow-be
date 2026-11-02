package com.traveler.web.domain.useractivity.dto;

import com.traveler.web.domain.useractivity.enums.MatchType;
import com.traveler.web.global.exception.WebApiServiceException;
import com.traveler.web.global.exception.code.WebApiServiceErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;

public record DedupSpec(@NotEmpty @Valid List<Condition> conditions, @NotNull ExpirationTime expirationTime) {

    public record Condition(@NotBlank String field, String value, @NotNull MatchType matchType) {}

    public record ExpirationTime(
            @PositiveOrZero int days,
            @PositiveOrZero int hours,
            @PositiveOrZero int minutes,
            @PositiveOrZero int seconds) {
        public ExpirationTime {
            long totalSeconds = Duration.ofDays(days)
                    .plusHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .getSeconds();
            if (totalSeconds <= 0) {
                throw new WebApiServiceException(WebApiServiceErrorCode.DEDUP_EXPIRATION_TIME_NON_POSITIVE);
            }
        }
    }
}
