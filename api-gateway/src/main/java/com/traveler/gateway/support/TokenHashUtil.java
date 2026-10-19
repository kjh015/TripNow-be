package com.traveler.gateway.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TokenHashUtil {

    // 인스턴스화 방지
    private TokenHashUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 입력된 문자열을 SHA-256 알고리즘을 사용하여 해시 처리합니다.
     * @param token 원문 토큰
     * @return 16진수 문자열로 변환된 해시값
     */
    public static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("토큰 값은 비어있을 수 없습니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            // 이 예외는 Java 스펙상 SHA-256이 무조건 지원되므로 사실상 발생하지 않습니다.
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
