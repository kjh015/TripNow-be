package com.traveler.search.domain.post.repository;

import com.traveler.search.domain.post.document.PostDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 삭제 표시(tombstone) 문서를 제외하는 게시글 조회는 PostDocumentRepositoryCustom(PostDocumentRepositoryImpl)에서 구현한다.
 * ElasticsearchRepository에서 상속한 findById·findAll은 삭제 여부를 필터링하지 않으므로 삭제 표시 문서도 반환한다.
 */
public interface PostDocumentRepository
        extends ElasticsearchRepository<PostDocument, Long>, PostDocumentRepositoryCustom {}
