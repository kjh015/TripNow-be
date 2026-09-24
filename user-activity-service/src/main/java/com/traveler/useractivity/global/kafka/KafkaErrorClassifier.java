package com.traveler.useractivity.global.kafka;

import com.traveler.useractivity.global.exception.UserActivityServiceException;
import com.traveler.useractivity.global.exception.code.UserActivityServiceErrorCode;
import java.util.Set;

public final class KafkaErrorClassifier {

    // 5xx지만 규칙 자체의 문제라 다시 처리해도 결과가 같은 코드
    private static final Set<UserActivityServiceErrorCode> NON_RETRYABLE_SERVER_CODES =
            Set.of(UserActivityServiceErrorCode.INVALID_FILTER_RULE_SYNTAX);

    private KafkaErrorClassifier() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean isClientError(UserActivityServiceException uase) {
        int status = uase.getCode().getStatus();
        return status >= 400 && status < 500;
    }

    /**
     * 리스너 예외는 ListenerExecutionFailedException 등으로 감싸져 오므로 원인 체인에서
     * UserActivityServiceException을 찾아 재시도해도 결과가 같은 오류인지 판단합니다.
     */
    public static boolean isNonRetryable(Throwable exception) {
        UserActivityServiceException uase = findServiceException(exception);
        if (uase == null) {
            return false;
        }
        return isClientError(uase) || NON_RETRYABLE_SERVER_CODES.contains(uase.getCode());
    }

    private static UserActivityServiceException findServiceException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UserActivityServiceException uase) {
                return uase;
            }
            if (current.getCause() == current) {
                return null;
            }
            current = current.getCause();
        }
        return null;
    }
}
