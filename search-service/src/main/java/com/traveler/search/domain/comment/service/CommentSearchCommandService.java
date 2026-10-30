package com.traveler.search.domain.comment.service;

import com.traveler.search.domain.comment.dto.message.CommentSearchMessage;
import com.traveler.search.domain.comment.mapper.CommentDocumentMapper;
import com.traveler.search.domain.comment.repository.CommentDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CommentSearchCommandService {
    private final CommentDocumentRepository commentDocumentRepository;
    private final CommentDocumentMapper commentDocumentMapper;

    public void createDocument(CommentSearchMessage.CreatedDTO dto) {
        commentDocumentRepository.save(commentDocumentMapper.toCommentDocument(dto));
    }

    public void updateDocument(CommentSearchMessage.UpdatedDTO dto) {
        commentDocumentRepository.updatePartial(dto);
    }

    public void deleteDocument(CommentSearchMessage.DeletedDTO dto) {
        commentDocumentRepository.deleteById(dto.commentId());
    }

    public void deleteDocuments(CommentSearchMessage.BatchDeletedDTO dto) {
        commentDocumentRepository.deleteAllById(dto.commentIds());
    }
}
