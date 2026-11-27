package com.traveler.post.domain.post.dto.message;

import com.traveler.post.domain.post.enums.Category;
import com.traveler.post.domain.post.enums.Region;
import java.time.Instant;
import java.util.List;

public final class PostMessage {

    private PostMessage() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public record ImageInfo(String imageKey, int sortOrder) {}

    // version: 게시글 본문 버전(Post.contentVersion).
    // 검색 서버는 문서에 반영된 버전보다 높은 버전의 이벤트만 반영한다.
    // CREATED·UPDATED 이벤트는 게시글의 전체 본문 상태를 포함하므로, 이벤트 도착 순서와 관계없이 해당 이벤트만으로 문서를 생성하거나 갱신할 수 있다.
    // 관리자 복구(Restored) 이벤트도 동일한 전체 상태를 전달하기 위해 CreatedDTO를 사용한다.
    public record CreatedDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            Category category,
            Region region,
            String travelPlace,
            String address,
            Instant createdAt,
            List<ImageInfo> images,
            Long version) {}

    public record UpdatedDTO(
            Long postId,
            Long memberId,
            String title,
            String content,
            Category category,
            Region region,
            String travelPlace,
            String address,
            Instant createdAt,
            Instant updatedAt,
            List<ImageInfo> images,
            Long version) {}

    // 검색 서버는 문서를 지우지 않고 삭제 표시(tombstone)한다. 늦게 온 예전 버전의 생성 이벤트가 글을 되살리지 않게 한다
    public record DeletedDTO(Long postId, Long version, Instant deletedAt) {}

    public record ImagesDeleteDTO(Long postId, List<String> imageKeys) {
        public ImagesDeleteDTO {
            imageKeys = (imageKeys == null) ? java.util.List.of() : List.copyOf(imageKeys);
        }
    }

    // syncedAt: 발행 시각(epoch ms). 검색 서버는 이보다 오래된 통계로 덮어쓰지 않는다(재전송·순서 뒤바뀜 대비)
    public record UpdateStatDTO(
            Long postId, Double starAvg, Long commentCount, Long likeCount, Long viewCount, Long syncedAt) {}
}
