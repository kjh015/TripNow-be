package com.traveler.common.support.logging.mask;

/**
 * 로그에 원문을 남기면 안 되는 값(로그인 ID, 인가 코드 등)을 식별 가능한 수준으로만 줄인다.
 */
public final class LogMasking {

    private static final int VISIBLE_PREFIX_LENGTH = 2;
    private static final String EMPTY = "-";

    private LogMasking() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /** 앞 2자(값이 짧으면 절반)만 남기고 나머지는 '*'로 바꾼다. 예) traveler -> tr****** */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        int visible = Math.min(VISIBLE_PREFIX_LENGTH, value.length() / 2);
        return value.substring(0, visible) + "*".repeat(value.length() - visible);
    }

    /** 앞 length자만 남긴다. 일회용 코드처럼 길이가 길고 무작위인 값에 쓴다. 예) prefix(uuid, 8) */
    public static String prefix(String value, int length) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }
}
