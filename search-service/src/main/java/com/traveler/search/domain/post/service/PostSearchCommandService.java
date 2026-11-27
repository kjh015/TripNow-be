package com.traveler.search.domain.post.service;

import com.traveler.search.domain.post.dto.message.PostSearchMessage;
import com.traveler.search.domain.post.mapper.PostDocumentMapper;
import com.traveler.search.domain.post.repository.PostDocumentRepository;
import com.traveler.search.global.util.HangulUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 게시글 이벤트를 문서에 반영한다. 버전이 있는 이벤트는 문서의 postVersion보다 클 때만 반영해, 순서가 바뀌거나 다시 온 이벤트도 결과가
 * 같다. 삭제는 문서를 지우지 않고 삭제 표시(tombstone)한다
 */
@Service
@RequiredArgsConstructor
public class PostSearchCommandService {
    private final PostDocumentRepository postDocumentRepository;
    private final PostDocumentMapper postDocumentMapper;

    public void createDocument(PostSearchMessage.CreatedDTO dto) {
        String titleChosung = HangulUtils.extractChosung(dto.title());
        if (dto.version() == null) {
            // 버전 도입 전 메시지 호환
            postDocumentRepository.save(postDocumentMapper.toCreateDocument(dto, titleChosung));
            return;
        }
        postDocumentRepository.upsertContent(postDocumentMapper.toContentSnapshot(dto, titleChosung));
    }

    public void updateDocument(PostSearchMessage.UpdatedDTO dto) {
        if (dto.version() == null) {
            // 버전 도입 전 메시지 호환
            postDocumentRepository.updatePartial(dto);
            return;
        }
        String titleChosung = HangulUtils.extractChosung(dto.title());
        postDocumentRepository.upsertContent(postDocumentMapper.toContentSnapshot(dto, titleChosung));
    }

    public void deleteDocument(PostSearchMessage.DeletedDTO dto) {
        if (dto.version() == null) {
            // 버전 도입 전 메시지 호환
            postDocumentRepository.deleteById(dto.postId());
            return;
        }
        postDocumentRepository.markDeleted(dto);
    }

    public void updateStatistics(PostSearchMessage.StatUpdatedDTO dto) {
        postDocumentRepository.updateStatistics(postDocumentMapper.toStatUpdateDoc(dto));
    }
}
