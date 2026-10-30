package com.traveler.post.domain.like.mapper;

import com.traveler.post.domain.like.dto.event.LikeEvent;
import com.traveler.post.domain.like.dto.message.LikeMessage;
import com.traveler.post.domain.like.dto.response.LikeResponse;
import com.traveler.post.domain.like.entity.Like;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LikeMapper {

    // Response
    LikeResponse.StatsDTO toStatsDTO(PostStatsSummary stats);

    // Kafka
    @Mapping(source = "id", target = "likeId")
    @Mapping(source = "post.id", target = "postId")
    LikeMessage.AddedDTO toAddedMessage(Like like);

    @Mapping(source = "id", target = "likeId")
    LikeMessage.RemovedDTO toRemovedMessage(Like like);

    // Event
    default LikeEvent.Added toAddedEvent(Like like) {
        return new LikeEvent.Added(toAddedMessage(like));
    }

    default LikeEvent.BatchRemoved toBatchRemovedEvent(Long postId, List<Like> likes) {
        return new LikeEvent.BatchRemoved(new LikeMessage.BatchRemovedDTO(
                postId, likes.stream().map(Like::getId).toList()));
    }

    default LikeEvent.Removed toRemovedEvent(Like like) {
        return new LikeEvent.Removed(toRemovedMessage(like), like.getPost().getId());
    }
}
