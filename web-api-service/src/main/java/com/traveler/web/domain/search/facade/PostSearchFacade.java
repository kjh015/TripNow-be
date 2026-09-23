package com.traveler.web.domain.search.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.search.adapter.PostSearchClientAdapter;
import com.traveler.web.domain.search.client.dto.response.PostSearchClientResponse;
import com.traveler.web.domain.search.dto.request.PostSearchRequest;
import com.traveler.web.domain.search.dto.response.PostSearchResponse;
import com.traveler.web.domain.search.mapper.PostSearchMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostSearchFacade {
    private final PostSearchClientAdapter postSearchClientAdapter;
    private final PostSearchMapper postSearchMapper;
    private final MemberClientAdapter memberClientAdapter;

    public PageResponse<PostSearchResponse.ListDTO> search(PostSearchRequest.SearchDTO dto) {
        PageResponse<PostSearchClientResponse.SearchDTO> result = postSearchClientAdapter.search(dto);

        Set<Long> memberIds = result.content().stream()
                .map(PostSearchClientResponse.SearchDTO::memberId)
                .collect(Collectors.toSet());

        Map<Long, String> nicknameMap = memberClientAdapter.getNicknameMap(memberIds);

        // 게시글 데이터와 닉네임 Map 조합
        return result.map(clientDto -> {
            String nickname = nicknameMap.get(clientDto.memberId());
            return postSearchMapper.toSearchListResponse(clientDto, nickname);
        });
    }

    public PostSearchResponse.DetailDTO getPostDetail(Long postId) {
        PostSearchClientResponse.DetailDTO result = postSearchClientAdapter.getPost(postId);

        String nickname = memberClientAdapter.getMemberNickname(result.memberId());

        return postSearchMapper.toDetailResponse(result, nickname);
    }

    public PostSearchResponse.AutocompleteDTO autocomplete(String keyword) {
        return postSearchMapper.toAutocompleteResponse(postSearchClientAdapter.autocomplete(keyword));
    }

    public PageResponse<PostSearchResponse.ListDTO> getMyPosts(Pageable pageable) {
        PageResponse<PostSearchClientResponse.MyDTO> result = postSearchClientAdapter.getMyPosts(pageable);

        String nickname = memberClientAdapter.getMyNickname();

        return result.map(clientDto -> postSearchMapper.toMyListResponse(clientDto, nickname));
    }
}
