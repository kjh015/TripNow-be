package com.traveler.search.domain.post.service;

import com.traveler.common.api.converter.CursorConverter;
import com.traveler.common.api.converter.PageConverter;
import com.traveler.common.core.response.CursorResponse;
import com.traveler.common.core.response.PageResponse;
import com.traveler.search.domain.post.document.PostDocument;
import com.traveler.search.domain.post.dto.request.PostSearchRequest;
import com.traveler.search.domain.post.dto.response.PostSearchResponse;
import com.traveler.search.domain.post.mapper.PostDocumentMapper;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.domain.post.support.PostFeedCursorCodec;
import com.traveler.search.global.exception.SearchServiceException;
import com.traveler.search.global.exception.code.SearchServiceErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PostSearchQueryService {
    private final PostDocumentRepository postDocumentRepository;
    private final PostDocumentMapper postDocumentMapper;
    private final PostFeedCursorCodec postFeedCursorCodec;

    public PageResponse<PostSearchResponse.SearchDTO> search(PostSearchRequest.SearchDTO dto) {
        Pageable pageable = dto.toPageable();

        Page<PostDocument> result = postDocumentRepository.search(dto, pageable);

        return PageConverter.toPageResponse(result, postDocumentMapper::toSearchDTO);
    }

    public CursorResponse<PostSearchResponse.SearchDTO> getFeed(PostSearchRequest.FeedDTO dto) {
        List<Object> searchAfter = StringUtils.hasText(dto.cursor())
                ? postFeedCursorCodec.decode(dto.cursor(), dto.sort(), dto.direction())
                : null;

        // 한 건 더 읽어 다음 페이지가 있는지 판단한다
        List<SearchHit<PostDocument>> hits = postDocumentRepository.searchFeed(dto, searchAfter, dto.size() + 1);

        return CursorConverter.toCursorResponse(
                hits,
                dto.size(),
                hit -> postFeedCursorCodec.encode(dto.sort(), dto.direction(), hit.getSortValues()),
                hit -> postDocumentMapper.toSearchDTO(hit.getContent()));
    }

    public PostSearchResponse.AutocompleteDTO autocomplete(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new PostSearchResponse.AutocompleteDTO(List.of());
        }
        List<String> titles = postDocumentRepository.findAutocompleteTitles(keyword);

        return postDocumentMapper.toAutocompleteDTO(titles);
    }

    public PostSearchResponse.DetailDTO getPostDetail(Long postId) {
        PostDocument post = postDocumentRepository
                .findById(postId)
                .orElseThrow(() -> new SearchServiceException(SearchServiceErrorCode.POST_NOT_FOUND));

        return postDocumentMapper.toDetailDTO(post);
    }

    public PageResponse<PostSearchResponse.MyDTO> getMyPosts(Long memberId, Pageable pageable) {
        Page<PostDocument> posts = postDocumentRepository.findByMemberId(memberId, pageable);

        return PageConverter.toPageResponse(posts, postDocumentMapper::toMyDTO);
    }
}
