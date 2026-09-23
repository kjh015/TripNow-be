package com.traveler.web.domain.search.facade;

import com.traveler.common.core.response.PageResponse;
import com.traveler.web.domain.member.adapter.MemberClientAdapter;
import com.traveler.web.domain.search.adapter.CommentSearchClientAdapter;
import com.traveler.web.domain.search.client.dto.response.CommentSearchClientResponse;
import com.traveler.web.domain.search.dto.response.CommentSearchResponse;
import com.traveler.web.domain.search.mapper.CommentSearchMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentSearchFacade {
    private final CommentSearchClientAdapter commentSearchClientAdapter;
    private final CommentSearchMapper commentSearchMapper;
    private final MemberClientAdapter memberClientAdapter;

    public PageResponse<CommentSearchResponse.ListDTO> getComments(Long postId, Pageable pageable) {
        PageResponse<CommentSearchClientResponse.ListDTO> result =
                commentSearchClientAdapter.getComments(postId, pageable);

        Set<Long> memberIds = result.content().stream()
                .map(CommentSearchClientResponse.ListDTO::memberId)
                .collect(Collectors.toSet());

        Map<Long, String> nicknameMap = memberClientAdapter.getNicknameMap(memberIds);

        // 3. 데이터 조합
        return result.map(clientDto -> {
            String nickname = nicknameMap.get(clientDto.memberId());
            return commentSearchMapper.toListResponse(clientDto, nickname);
        });
    }

    public PageResponse<CommentSearchResponse.ListDTO> getMyComments(Pageable pageable) {
        PageResponse<CommentSearchClientResponse.MyDTO> result = commentSearchClientAdapter.getMyComments(pageable);

        String nickname = memberClientAdapter.getMyNickname();

        return result.map(clientDto -> commentSearchMapper.toMyListResponse(clientDto, nickname));
    }
}
