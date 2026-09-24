package com.traveler.post.domain.post.support;

import com.traveler.post.global.s3.ImageType;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 게시글 이미지 S3 키 형식의 단일 출처. 발급(create)과 검증(isIssuedFor)이 같은 규칙을 쓴다. */
public final class PostImageKey {

    private static final String KEY_FORMAT = "posts/%d/%s.%s";
    private static final String PREFIX_FORMAT = "posts/%d/";
    // KEY_FORMAT의 파일명 부분({UUID}.{확장자}). 슬래시·'..'가 끼어들 여지가 없도록 전체 일치로 검사한다.
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.([a-z]+)$");

    private PostImageKey() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String create(Long memberId, ImageType imageType) {
        return String.format(KEY_FORMAT, memberId, UUID.randomUUID(), imageType.getExtension());
    }

    /** 해당 회원에게 발급된 형식의 키인지 확인한다(다른 회원 prefix·경로 조작·허용하지 않는 확장자 차단). */
    public static boolean isIssuedFor(Long memberId, String key) {
        String prefix = String.format(PREFIX_FORMAT, memberId);
        if (key == null || !key.startsWith(prefix)) {
            return false;
        }

        Matcher matcher = FILE_NAME_PATTERN.matcher(key.substring(prefix.length()));
        return matcher.matches() && ImageType.fromExtension(matcher.group(1)).isPresent();
    }
}
