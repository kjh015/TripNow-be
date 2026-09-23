package com.traveler.web.domain.search.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.search.adapter.LikeSearchClientAdapter;
import com.traveler.web.domain.search.client.dto.response.LikeSearchClientResponse;
import com.traveler.web.domain.search.dto.response.PostSearchResponse;
import com.traveler.web.domain.search.mapper.LikeSearchMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeSearchFacade {
    private final LikeSearchClientAdapter likeSearchClientAdapter;
    private final LikeSearchMapper likeSearchMapper;
    private final MemberClientAdapter memberClientAdapter;

    public PageResponse<PostSearchResponse.ListDTO> getMyLikePosts(Pageable pageable) {
        PageResponse<LikeSearchClientResponse.MyDTO> result = likeSearchClientAdapter.getMyLikePosts(pageable);

        Set<Long> memberIds = result.content().stream()
                .map(LikeSearchClientResponse.MyDTO::memberId)
                .collect(Collectors.toSet());

        Map<Long, String> nicknameMap = memberClientAdapter.getNicknameMap(memberIds);

        // 3. 데이터 조합
        return result.map(clientDto -> {
            String nickname = nicknameMap.get(clientDto.memberId());
            return likeSearchMapper.toMyListResponse(clientDto, nickname);
        });
    }
}
