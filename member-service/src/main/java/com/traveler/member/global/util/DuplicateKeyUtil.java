package com.traveler.member.global.util;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;

public final class DuplicateKeyUtil {

    // 인스턴스화 방지
    private DuplicateKeyUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 무결성 위반 예외가 유니크 제약(중복 키) 위반인지 판별합니다.
     * @param e 저장 중 발생한 무결성 위반 예외
     * @return 중복 키 위반이면 true, 그 외(Not Null 위반, 데이터 잘림 등)는 false
     */
    public static boolean isDuplicateKeyError(DataIntegrityViolationException e) {
        Throwable rootCause = e.getRootCause();

        if (rootCause instanceof SQLException sqlException) {
            // MySQL/MariaDB: 1062, PostgreSQL SQLState: 23505
            return sqlException.getErrorCode() == 1062 || "23505".equals(sqlException.getSQLState());
        }
        return false;
    }
}
