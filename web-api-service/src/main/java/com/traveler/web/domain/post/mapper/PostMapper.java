package com.traveler.web.domain.post.mapper;

import com.traveler.web.domain.post.client.dto.request.PostClientRequest;
import com.traveler.web.domain.post.client.dto.response.AdminPostClientResponse;
import com.traveler.web.domain.post.client.dto.response.PostClientResponse;
import com.traveler.web.domain.post.dto.request.PostRequest;
import com.traveler.web.domain.post.dto.response.AdminPostResponse;
import com.traveler.web.domain.post.dto.response.PostResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {
    PostClientRequest.CreateDTO toCreateClientRequest(PostRequest.CreateDTO dto);

    @Mapping(source = "memberNickname", target = "memberNickname")
    @Mapping(target = "starAvg", constant = "0.0")
    @Mapping(target = "viewCount", constant = "0L")
    @Mapping(target = "likeCount", constant = "0L")
    @Mapping(target = "commentCount", constant = "0L")
    PostResponse.CreateDTO toCreateResponse(PostClientResponse.CreateDTO result, String memberNickname);

    PostClientRequest.UpdateDTO toUpdateClientRequest(PostRequest.UpdateDTO dto);

    @Mapping(source = "memberNickname", target = "memberNickname")
    PostResponse.UpdateDTO toUpdateResponse(PostClientResponse.UpdateDTO result, String memberNickname);

    PostResponse.DeleteDTO toDeleteResponse(PostClientResponse.DeleteDTO result);

    PostResponse.PresignedUrlDTO toPresignedUrlResponse(PostClientResponse.PresignedUrlDTO result);

    // Admin
    AdminPostResponse.ListDTO toAdminListResponse(AdminPostClientResponse.ListDTO clientResponse);

    AdminPostResponse.DetailDTO toAdminDetailResponse(AdminPostClientResponse.DetailDTO clientResponse);

    AdminPostResponse.DeleteDTO toAdminDeleteResponse(AdminPostClientResponse.DeleteDTO clientResponse);

    AdminPostResponse.RestoreDTO toAdminRestoreResponse(AdminPostClientResponse.RestoreDTO clientResponse);

    AdminPostResponse.PermanentDeleteDTO toAdminPermanentDeleteResponse(
            AdminPostClientResponse.PermanentDeleteDTO clientResponse);
}
