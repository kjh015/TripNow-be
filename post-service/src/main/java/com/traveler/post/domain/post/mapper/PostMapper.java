package com.traveler.post.domain.post.mapper;

import com.traveler.post.domain.post.dto.event.PostEvent;
import com.traveler.post.domain.post.dto.message.PostMessage;
import com.traveler.post.domain.post.dto.request.PostRequest;
import com.traveler.post.domain.post.dto.response.PostResponse;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostImage;
import com.traveler.post.domain.post.entity.TravelPlace;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {
    @Mapping(target = "travelPlace", source = "travelPlace")
    @Mapping(target = "images", ignore = true)
    Post toCreateEntity(PostRequest.CreateDTO dto, TravelPlace travelPlace, Long memberId);

    @Mapping(source = "id", target = "postId")
    PostResponse.CreateDTO toCreateDTO(Post post);

    @Mapping(source = "id", target = "postId")
    PostResponse.UpdateDTO toUpdateDTO(Post post);

    @Mapping(source = "id", target = "postId")
    PostResponse.DeleteDTO toDeleteDTO(Post post);

    // Kafka Message
    @Mapping(source = "id", target = "postId")
    @Mapping(source = "travelPlace.name", target = "travelPlace")
    @Mapping(source = "travelPlace.category", target = "category")
    @Mapping(source = "travelPlace.region", target = "region")
    @Mapping(source = "travelPlace.address", target = "address")
    PostMessage.CreatedDTO toCreatedMsgDTO(Post post);

    PostMessage.ImageInfo toImageInfo(PostImage postImage);

    @Mapping(source = "id", target = "postId")
    @Mapping(source = "travelPlace.name", target = "travelPlace")
    @Mapping(source = "travelPlace.category", target = "category")
    @Mapping(source = "travelPlace.region", target = "region")
    @Mapping(source = "travelPlace.address", target = "address")
    PostMessage.UpdatedDTO toUpdatedMsgDTO(Post post);

    @Mapping(source = "id", target = "postId")
    PostMessage.DeletedDTO toDeletedMsgDTO(Post post);

    default PostMessage.ImagesDeleteDTO toDeleteImagesMsgDTO(Long postId, List<String> imageKeys) {
        if (imageKeys == null) return null;
        return new PostMessage.ImagesDeleteDTO(postId, imageKeys);
    }

    // Event
    @Mapping(target = "postMsg", source = "post")
    PostEvent.Created toCreatedEvent(Post post);

    @Mapping(target = "postMsg", source = "post")
    PostEvent.Updated toUpdatedEvent(Post post);

    @Mapping(target = "postMsg", source = "post")
    PostEvent.Deleted toDeletedEvent(Post post);

    @Mapping(target = "postMsg", source = "post")
    PostEvent.Restored toRestoredEvent(Post post);

    default PostEvent.ImagesDelete toImageDeleteEvent(Long postId, List<String> imageKeys) {
        PostMessage.ImagesDeleteDTO dto = toDeleteImagesMsgDTO(postId, imageKeys);
        return (dto == null) ? null : new PostEvent.ImagesDelete(dto);
    }

    default PostEvent.ImagesDeleteBatch toImageDeleteBatchEvent(Long postId, List<String> imageKeys) {
        PostMessage.ImagesDeleteDTO dto = toDeleteImagesMsgDTO(postId, imageKeys);
        return (dto == null) ? null : new PostEvent.ImagesDeleteBatch(dto);
    }

    default PostEvent.StatUpdated toStatUpdatedEvent(Post post, PostStatsSummary stats, long syncedAt) {
        return new PostEvent.StatUpdated(new PostMessage.UpdateStatDTO(
                post.getId(), stats.starAvg(), stats.commentCount(), stats.likeCount(), post.getViewCount(), syncedAt));
    }
}
