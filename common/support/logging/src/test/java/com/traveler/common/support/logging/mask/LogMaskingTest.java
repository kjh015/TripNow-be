package com.traveler.common.support.logging.mask;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    @DisplayName("앞 2자만 남기고 나머지는 가린다. 짧은 값은 절반만 남긴다")
    void mask() {
        assertThat(LogMasking.mask("traveler")).isEqualTo("tr******");
        assertThat(LogMasking.mask("abc")).isEqualTo("a**");
        assertThat(LogMasking.mask("a")).isEqualTo("*");
        assertThat(LogMasking.mask(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("앞 length자만 남긴다")
    void prefix() {
        assertThat(LogMasking.prefix("0f8fad5b-d9cb-469f-a165-70867728950e", 8)).isEqualTo("0f8fad5b");
        assertThat(LogMasking.prefix("short", 8)).isEqualTo("short");
        assertThat(LogMasking.prefix("", 8)).isEqualTo("-");
    }
}
