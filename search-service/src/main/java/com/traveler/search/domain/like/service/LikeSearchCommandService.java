package com.traveler.search.domain.like.service;

import com.traveler.search.domain.like.dto.message.LikeSearchMessage;
import com.traveler.search.domain.like.mapper.LikeDocumentMapper;
import com.traveler.search.domain.like.repository.LikeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class LikeSearchCommandService {
    private final LikeDocumentRepository likeDocumentRepository;
    private final LikeDocumentMapper likeDocumentMapper;

    public void addDocument(LikeSearchMessage.AddedDTO dto) {
        likeDocumentRepository.save(likeDocumentMapper.toLikeDocument(dto));
    }

    public void removeDocument(LikeSearchMessage.RemovedDTO dto) {
        likeDocumentRepository.deleteById(dto.likeId());
    }

    public void removeDocuments(LikeSearchMessage.BatchRemovedDTO dto) {
        likeDocumentRepository.deleteAllById(dto.likeIds());
    }
}
