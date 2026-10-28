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
            List<ImageInfo> images) {}

    public record UpdatedDTO(
            Long postId,
            String title,
            String content,
            Category category,
            Region region,
            String travelPlace,
            String address,
            Instant updatedAt,
            List<ImageInfo> images) {}

    public record DeletedDTO(Long postId) {}

    public record ImagesDeleteDTO(Long postId, List<String> imageKeys) {
        public ImagesDeleteDTO {
            imageKeys = (imageKeys == null) ? java.util.List.of() : List.copyOf(imageKeys);
        }
    }

    // syncedAt: 발행 시각(epoch ms). 검색 서버는 이보다 오래된 통계로 덮어쓰지 않는다(재전송·순서 뒤바뀜 대비)
    public record UpdateStatDTO(
            Long postId, Double starAvg, Long commentCount, Long likeCount, Long viewCount, Long syncedAt) {}

    public record PostViewedDTO(Long postId, String traceId) {}
}
