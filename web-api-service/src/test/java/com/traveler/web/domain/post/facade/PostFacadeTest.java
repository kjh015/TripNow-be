package com.traveler.web.domain.post.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.post.adapter.PostClientAdapter;
import com.traveler.web.domain.post.client.dto.response.PostClientResponse;
import com.traveler.web.domain.post.dto.request.PostRequest;
import com.traveler.web.domain.post.dto.response.PostResponse;
import com.traveler.web.domain.post.mapper.PostMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostFacadeTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-19T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-11-19T01:00:00Z");

    @Mock
    private PostClientAdapter postClientAdapter;

    @Mock
    private MemberClientAdapter memberClientAdapter;

    private PostFacade facade;

    @BeforeEach
    void setUp() {
        // 매퍼는 MapStruct가 만든 진짜 구현체를 쓴다
        facade = new PostFacade(postClientAdapter, memberClientAdapter, Mappers.getMapper(PostMapper.class));
    }

    @Test
    @DisplayName("작성 응답에 저장된 게시글 전체·버전·작성자 닉네임과 새 글의 통계(0)를 담는다")
    void createIncludesNicknameAndStats() {
        given(postClientAdapter.createPost(any())).willReturn(created());
        given(memberClientAdapter.getMyNickname()).willReturn("여행자");

        PostResponse.CreateDTO response = facade.createPost(createRequest());

        assertThat(response.postId()).isEqualTo(101L);
        assertThat(response.memberNickname()).isEqualTo("여행자");
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.category()).isEqualTo("FOOD");
        assertThat(response.images()).containsExactly(new PostResponse.ImageInfo("posts/1/a.png", 1));
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.version()).isZero();
        assertThat(response.starAvg()).isZero();
        assertThat(response.viewCount()).isZero();
        assertThat(response.likeCount()).isZero();
        assertThat(response.commentCount()).isZero();
    }

    @Test
    @DisplayName("작성 뒤 닉네임 조회가 예외를 던져도 응답은 성공이고 닉네임만 null이다")
    void createSucceedsWhenNicknameFails() {
        given(postClientAdapter.createPost(any())).willReturn(created());
        given(memberClientAdapter.getMyNickname()).willThrow(new IllegalStateException("member-service 4xx"));

        PostResponse.CreateDTO response = facade.createPost(createRequest());

        assertThat(response.postId()).isEqualTo(101L);
        assertThat(response.memberNickname()).isNull();
        assertThat(response.version()).isZero();
    }

    @Test
    @DisplayName("수정 응답에 저장된 게시글 전체·버전·작성자 닉네임을 담고, 닉네임 조회 실패는 null로 둔다")
    void updateIncludesNickname() {
        given(postClientAdapter.updatePost(eq(101L), any())).willReturn(updated());
        given(memberClientAdapter.getMyNickname()).willReturn("여행자").willThrow(new RuntimeException("down"));

        PostResponse.UpdateDTO first = facade.updatePost(101L, updateRequest());
        PostResponse.UpdateDTO second = facade.updatePost(101L, updateRequest());

        assertThat(first.memberNickname()).isEqualTo("여행자");
        assertThat(first.title()).isEqualTo("새 제목");
        assertThat(first.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(first.version()).isEqualTo(1L);
        assertThat(second.memberNickname()).isNull();
        assertThat(second.version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("삭제 응답에 삭제 시각과 버전을 담는다")
    void deleteIncludesVersion() {
        given(postClientAdapter.deletePost(101L)).willReturn(new PostClientResponse.DeleteDTO(101L, UPDATED_AT, 2L));

        PostResponse.DeleteDTO response = facade.deletePost(101L);

        assertThat(response).isEqualTo(new PostResponse.DeleteDTO(101L, UPDATED_AT, 2L));
    }

    private static PostClientResponse.CreateDTO created() {
        return new PostClientResponse.CreateDTO(
                101L,
                1L,
                "제목",
                "본문",
                "황리단길",
                "경주시",
                "FOOD",
                "SEOUL",
                List.of(new PostClientResponse.ImageInfo("posts/1/a.png", 1)),
                CREATED_AT,
                CREATED_AT,
                0L);
    }

    private static PostClientResponse.UpdateDTO updated() {
        return new PostClientResponse.UpdateDTO(
                101L, 1L, "새 제목", "본문", "황리단길", "경주시", "FOOD", "SEOUL", List.of(), CREATED_AT, UPDATED_AT, 1L);
    }

    private static PostRequest.CreateDTO createRequest() {
        return new PostRequest.CreateDTO("제목", "본문", "황리단길", "경주시", null, null, List.of("temp/1/a.png"));
    }

    private static PostRequest.UpdateDTO updateRequest() {
        return new PostRequest.UpdateDTO("새 제목", "본문", "황리단길", "경주시", null, null, List.of());
    }
}
