package com.traveler.post.domain.post.mapper;

import com.traveler.post.domain.post.dto.response.AdminPostResponse;
import com.traveler.post.domain.post.dto.result.PostStatsSummary;
import com.traveler.post.domain.post.entity.Post;
import com.traveler.post.domain.post.entity.PostImage;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminPostMapper {

    @Mapping(source = "post.id", target = "postId")
    @Mapping(source = "post.deleted", target = "isDeleted")
    @Mapping(source = "post.createdAt", target = "createdAt")
    AdminPostResponse.ListDTO toListDTO(Post post, PostStatsSummary stats);

    @Mapping(source = "post.id", target = "postId")
    @Mapping(source = "post.deleted", target = "isDeleted")
    @Mapping(source = "post.createdAt", target = "createdAt")
    @Mapping(source = "post.updatedAt", target = "updatedAt")
    @Mapping(source = "post.travelPlace.category", target = "category")
    @Mapping(source = "post.travelPlace.region", target = "region")
    @Mapping(source = "post.travelPlace.name", target = "travelPlace")
    @Mapping(source = "post.travelPlace.address", target = "address")
    AdminPostResponse.DetailDTO toDetailDTO(Post post, PostStatsSummary stats);

    @Mapping(source = "id", target = "postId")
    AdminPostResponse.DeleteDTO toDeleteDTO(Post post);

    @Mapping(source = "id", target = "postId")
    AdminPostResponse.RestoreDTO toRestoreDTO(Post post);

    default List<String> toImageKeys(List<PostImage> images) {
        if (images == null) return List.of();
        return images.stream()
                .sorted(Comparator.comparingInt(PostImage::getSortOrder))
                .map(PostImage::getImageKey)
                .toList();
    }
}
