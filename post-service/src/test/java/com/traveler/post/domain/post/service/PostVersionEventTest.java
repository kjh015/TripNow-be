package com.traveler.post.domain.post.service;

import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY;
import static com.traveler.post.testsupport.fixture.PostFixture.MEMBER_1_KEY_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.traveler.common.support.logging.audit.AuditLogger;
import com.traveler.post.domain.post.dto.event.PostEvent;
import com.traveler.post.domain.post.dto.message.PostMessage;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.TravelPlace;
import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import com.traveler.post.domain.post.mapper.AdminPostMapper;
import com.traveler.post.domain.post.mapper.PostMapper;
import com.traveler.post.domain.post.mapper.PostMapperImpl;
import com.traveler.post.domain.post.mapper.TravelPlaceMapper;
import com.traveler.post.domain.post.repository.PostRepository;
import com.traveler.post.domain.post.repository.PostStatsRepository;
import com.traveler.post.domain.post.support.PostHardDeleter;
import com.traveler.post.domain.post.support.PostStatsSlots;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;

/** 본문을 바꾸는 쓰기가 올린 본문 버전(contentVersion)을 이벤트와 응답에 그대로 싣는지 확인한다. 매퍼는 진짜를 쓴다. */
class PostVersionEventTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PostHardDeleter postHardDeleter = mock(PostHardDeleter.class);
    private final PostMapper postMapper = new PostMapperImpl();

    private final PostService postService = new PostService(
            postRepository,
            mock(PostStatsRepository.class),
            mock(PostStatsSlots.class),
            postMapper,
            mock(TravelPlaceMapper.class),
            eventPublisher,
            postHardDeleter);

    private final AdminPostService adminPostService = new AdminPostService(
            postRepository,
            mock(PostStatsSlots.class),
            postHardDeleter,
            mock(AdminPostMapper.class),
            postMapper,
            eventPublisher,
            new AuditLogger());

    @Test
    @DisplayName("수정: 잠금 조회로 읽고, UPDATED 이벤트·응답에 올라간 버전과 게시글 전체 상태를 싣는다")
    void updatePublishesSnapshotWithVersion() {
        Post post = post();
        given(postRepository.findByIdWithDetailsForUpdate(POST_ID)).willReturn(Optional.of(post));

        PostResponse.UpdateDTO response =
                postService.updatePost(POST_ID, MEMBER_ID, updateDto("새 제목", "새 본문", "새 장소"), null, List.of());

        PostMessage.UpdatedDTO msg = lastEvent(PostEvent.Updated.class).postMsg();
        assertThat(post.getContentVersion()).isEqualTo(1L);
        assertThat(msg.version()).isEqualTo(post.getContentVersion());
        assertThat(msg.memberId()).isEqualTo(MEMBER_ID);
        assertThat(msg.title()).isEqualTo("새 제목");
        assertThat(msg.travelPlace()).isEqualTo("새 장소");
        assertThat(msg.category()).isEqualTo(Category.FOOD);
        assertThat(response.version()).isEqualTo(1L);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.travelPlace()).isEqualTo("새 장소");
        assertThat(response.category()).isEqualTo("FOOD");
        assertThat(response.region()).isEqualTo("SEOUL");
        // updatedAt(Auditing)이 이벤트·응답 전에 채워지도록 flush한다
        verify(postRepository).flush();
    }

    @Test
    @DisplayName("수정: 제목·본문은 그대로 두고 장소·이미지만 바꿔도 버전이 오른다")
    void updateOnlyPlaceAndImagesIncreasesVersion() {
        Post post = post();
        post.setImages(List.of(MEMBER_1_KEY));
        given(postRepository.findByIdWithDetailsForUpdate(POST_ID)).willReturn(Optional.of(post));

        PostResponse.UpdateDTO response = postService.updatePost(
                POST_ID,
                MEMBER_ID,
                updateDto(post.getTitle(), post.getContent(), "다른 장소"),
                List.of(MEMBER_1_KEY_2, MEMBER_1_KEY),
                List.of(MEMBER_1_KEY_2));

        assertThat(lastEvent(PostEvent.Updated.class).postMsg().version()).isEqualTo(1L);
        // 응답 이미지는 정렬 순서대로
        assertThat(response.images())
                .extracting(PostResponse.ImageInfo::imageKey)
                .containsExactly(MEMBER_1_KEY_2, MEMBER_1_KEY);
    }

    @Test
    @DisplayName("작성: CREATED 이벤트와 응답의 버전은 0이다")
    void createPublishesVersionZero() {
        given(postRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        PostService service = new PostService(
                postRepository,
                mock(PostStatsRepository.class),
                mock(PostStatsSlots.class),
                postMapper,
                new com.traveler.post.domain.post.mapper.TravelPlaceMapperImpl(),
                eventPublisher,
                postHardDeleter);

        PostResponse.CreateDTO response = service.createPost(
                MEMBER_ID,
                new PostRequest.CreateDTO("제목", "본문", "장소", "주소", Category.FOOD, Region.SEOUL, List.of()),
                List.of(MEMBER_1_KEY));

        PostMessage.CreatedDTO msg = lastEvent(PostEvent.Created.class).postMsg();
        assertThat(msg.version()).isZero();
        assertThat(response.version()).isZero();
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.travelPlace()).isEqualTo("장소");
        assertThat(response.images())
                .extracting(PostResponse.ImageInfo::imageKey)
                .containsExactly(MEMBER_1_KEY);
    }

    @Test
    @DisplayName("삭제: 잠금 조회로 읽고, DELETED 이벤트·응답에 올라간 버전과 삭제 시각을 싣는다")
    void deletePublishesVersionAndDeletedAt() {
        Post post = post();
        given(postRepository.findByIdForUpdate(POST_ID)).willReturn(Optional.of(post));

        PostResponse.DeleteDTO response = postService.deletePost(POST_ID, MEMBER_ID);

        PostMessage.DeletedDTO msg = lastEvent(PostEvent.Deleted.class).postMsg();
        assertThat(msg.version()).isEqualTo(1L).isEqualTo(post.getContentVersion());
        assertThat(msg.deletedAt()).isEqualTo(post.getDeletedAt()).isNotNull();
        assertThat(response.version()).isEqualTo(1L);
        assertThat(response.deletedAt()).isEqualTo(post.getDeletedAt());
    }

    @Test
    @DisplayName("탈퇴 정리: 잠금 조회로 읽고 게시글마다 올라간 버전으로 DELETED를 발행한다")
    void deleteByMemberPublishesVersions() {
        Post first = post();
        given(postRepository.findForUpdateByMemberIdOrderByIdAsc(MEMBER_ID, Limit.of(10)))
                .willReturn(List.of(first));

        postService.deleteByMember(MEMBER_ID, 10);

        assertThat(lastEvent(PostEvent.Deleted.class).postMsg().version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("관리자 삭제·복구: 잠금 조회로 읽고 이벤트 버전이 엔티티 버전과 같다")
    void adminDeleteAndRestore() {
        Post post = post();
        given(postRepository.findByIdForAdminWithLock(POST_ID)).willReturn(Optional.of(post));

        adminPostService.deletePost(POST_ID);
        assertThat(lastEvent(PostEvent.Deleted.class).postMsg().version()).isEqualTo(1L);

        adminPostService.restorePost(POST_ID);
        PostMessage.CreatedDTO restored = lastEvent(PostEvent.Restored.class).postMsg();
        assertThat(restored.version()).isEqualTo(2L).isEqualTo(post.getContentVersion());
    }

    @Test
    @DisplayName("영구 삭제: 소프트 삭제 전 글이면 버전을 올린 DELETED를 발행한다")
    void permanentDeleteOfActivePostPublishesHigherVersion() {
        Post post = post();
        post.update("수정", "수정"); // 버전 1
        long before = post.getContentVersion();
        given(postRepository.findByIdForAdminWithLock(POST_ID)).willReturn(Optional.of(post));

        adminPostService.permanentlyDeletePost(POST_ID);

        PostMessage.DeletedDTO msg = lastEvent(PostEvent.Deleted.class).postMsg();
        assertThat(msg.version()).isGreaterThan(before);
        assertThat(msg.deletedAt()).isNotNull();
    }

    private <T> T lastEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<T> events = captor.getAllValues().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
        assertThat(events).isNotEmpty();
        return events.getLast();
    }

    private static Post post() {
        return Post.builder()
                .id(POST_ID)
                .memberId(MEMBER_ID)
                .title("제목")
                .content("본문")
                .travelPlace(TravelPlace.builder()
                        .name("장소")
                        .address("주소")
                        .category(Category.FOOD)
                        .region(Region.SEOUL)
                        .build())
                .build();
    }

    private static PostRequest.UpdateDTO updateDto(String title, String content, String place) {
        return new PostRequest.UpdateDTO(title, content, place, "주소", Category.FOOD, Region.SEOUL, List.of());
    }
}
