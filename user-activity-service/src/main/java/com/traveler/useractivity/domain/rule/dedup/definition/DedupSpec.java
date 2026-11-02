package com.traveler.useractivity.domain.rule.dedup.definition;

import com.traveler.useractivity.domain.rule.dedup.enums.MatchType;
import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.List;

/**
 * 하나의 중복 제거 단위
 */
public record DedupSpec(@NotEmpty @Valid List<Condition> conditions, @NotNull @Valid ExpirationTime expirationTime) {

    /**
     * 개별 조건식
     */
    public record Condition(
            @NotBlank String field, // (예: "user_id")
            String value, // matchType이 ANY_VALUE일 때는 null 허용
            @NotNull MatchType matchType // (EXACT_MATCH / ANY_VALUE)
            ) {
        public Condition {
            if (matchType == MatchType.EXACT_MATCH && (value == null || value.isBlank())) {
                throw new UserActivityServiceException(UserActivityServiceErrorCode.DEDUP_CONDITION_VALUE_REQUIRED);
            }
        }
    }

    /**
     * 만료 시간 정보
     */
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
                throw new UserActivityServiceException(UserActivityServiceErrorCode.DEDUP_EXPIRATION_TIME_NON_POSITIVE);
            }
        }

        public long toTotalSeconds() {
            return Duration.ofDays(days)
                    .plusHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .getSeconds();
        }
    }
}
