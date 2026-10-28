package com.traveler.search.domain.post.mapper;

import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.dto.response.PostSearchResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostDocumentMapper {

    @Mapping(source = "msg.postId", target = "id")
    @Mapping(source = "titleChosung", target = "titleChosung")
    PostDocument toCreateDocument(PostSearchMessage.CreatedDTO msg, String titleChosung);

    @Mapping(source = "id", target = "postId")
    PostSearchResponse.SearchDTO toSearchDTO(PostDocument doc);

    @Mapping(source = "id", target = "postId")
    PostSearchResponse.DetailDTO toDetailDTO(PostDocument doc);

    default PostSearchResponse.AutocompleteDTO toAutocompleteDTO(List<String> titles) {
        if (titles == null) {
            return new PostSearchResponse.AutocompleteDTO(List.of());
        }

        List<String> distinctTitles = titles.stream().distinct().toList();

        return new PostSearchResponse.AutocompleteDTO(distinctTitles);
    }

    @Mapping(source = "id", target = "postId")
    PostSearchResponse.MyDTO toMyDTO(PostDocument doc);

    default PostSearchMessage.StatUpdateDoc toStatUpdateDoc(PostSearchMessage.StatUpdatedDTO msg) {
        if (msg == null) return null;

        // 점수 계산
        double score = calculatePopularityScore(msg);

        // record 생성 및 반환
        return new PostSearchMessage.StatUpdateDoc(
                msg.postId(),
                msg.starAvg(),
                msg.commentCount(),
                msg.likeCount(),
                msg.viewCount(),
                score,
                score,
                msg.syncedAt() == null ? 0L : msg.syncedAt());
    }

    // 가중치 계산 로직
    private double calculatePopularityScore(PostSearchMessage.StatUpdatedDTO msg) {
        double score =
                (msg.likeCount() * 5.0) + (msg.commentCount() * 3.0) + (msg.viewCount() * 0.1) + (msg.starAvg() * 10.0);

        // rank_feature는 0보다 커야 하므로 최소값 보정
        return Math.max(score, 0.0001);
    }
}
