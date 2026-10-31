package com.traveler.post.testsupport.fixture;

import com.traveler.post.domain.post.entity.Post;

/** post-service 테스트가 함께 쓰는 게시글·이미지 키 데이터. */
public final class PostFixture {

    /** 1번 회원에게 발급되는 형식(temp/{memberId}/{UUID}.{확장자})의 임시 키 */
    public static final String MEMBER_1_TEMP_KEY = "temp/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    /** 1번 회원의 두 번째 임시 키 (확장자가 다르다) */
    public static final String MEMBER_1_TEMP_KEY_2 = "temp/1/0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d.jpg";

    /** 2번 회원의 임시 키. 1번 회원 요청에 섞이면 거부되어야 한다 */
    public static final String MEMBER_2_TEMP_KEY = "temp/2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    /** MEMBER_1_TEMP_KEY를 확정한 보관 키(posts/{memberId}/{UUID}.{확장자}) */
    public static final String MEMBER_1_KEY = "posts/1/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    /** 1번 회원의 두 번째 이미지 키 (확장자가 다르다) */
    public static final String MEMBER_1_KEY_2 = "posts/1/0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d.jpg";

    /** 2번 회원의 보관 키. 1번 회원 요청에 섞이면 거부되어야 한다 */
    public static final String MEMBER_2_KEY = "posts/2/3f2b8c1e-5d4a-4e6b-9c7d-1a2b3c4d5e6f.png";

    private PostFixture() {}

    /** 작성자만 정한 게시글. 저장 전이라 id는 비어 있다. */
    public static Post post(Long memberId) {
        return Post.builder().memberId(memberId).title("제목").content("본문").build();
    }

    /** id까지 정한 게시글. Repository를 mock으로 두는 단위 테스트에서 저장된 게시글처럼 쓴다. */
    public static Post post(Long id, Long memberId) {
        return Post.builder()
                .id(id)
                .memberId(memberId)
                .title("제목")
                .content("본문")
                .build();
    }
}
