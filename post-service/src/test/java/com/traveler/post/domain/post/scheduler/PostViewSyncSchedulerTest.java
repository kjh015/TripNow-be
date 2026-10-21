package com.traveler.post.domain.post.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.domain.post.repository.PostViewRepository;
import com.traveler.post.domain.post.service.PostService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostViewSyncSchedulerTest {

    private static final String LOCK_TOKEN = "lock-token";

    @Mock
    private PostViewRepository postViewRepository;

    @Mock
    private PostService postService;

    @InjectMocks
    private PostViewSyncScheduler scheduler;

    @Test
    @DisplayName("락을 얻지 못하면 버퍼를 건드리지 않고 건너뛴다")
    void skipsWhenLockNotAcquired() {
        given(postViewRepository.tryAcquireFlushLock(any())).willReturn(null);

        scheduler.flushViewCounts();

        verify(postViewRepository, never()).prepareProcessingBuffer();
        verify(postViewRepository, never()).releaseFlushLock(any());
        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("처리할 조회수가 없으면 DB 동기화 없이 락만 해제한다")
    void releasesLockWhenNothingToFlush() {
        given(postViewRepository.tryAcquireFlushLock(any())).willReturn(LOCK_TOKEN);
        given(postViewRepository.prepareProcessingBuffer()).willReturn(false);

        scheduler.flushViewCounts();

        verifyNoInteractions(postService);
        verify(postViewRepository).releaseFlushLock(LOCK_TOKEN);
    }

    @Test
    @DisplayName("동기화에 성공하면 처리용 버퍼를 지우고 락을 해제한다")
    void deletesBufferAfterSuccessfulFlush() {
        Map<Object, Object> counts = Map.of("10", "3");
        given(postViewRepository.tryAcquireFlushLock(any())).willReturn(LOCK_TOKEN);
        given(postViewRepository.prepareProcessingBuffer()).willReturn(true);
        given(postViewRepository.getProcessingViewCounts()).willReturn(counts);

        scheduler.flushViewCounts();

        verify(postService).flushViewCountsToDB(counts);
        verify(postViewRepository).deleteProcessingBuffer();
        verify(postViewRepository).releaseFlushLock(LOCK_TOKEN);
    }

    @Test
    @DisplayName("DB 동기화가 실패하면 처리용 버퍼를 보존해 다음 주기에 재시도하고, 락은 해제한다")
    void keepsBufferWhenFlushFails() {
        Map<Object, Object> counts = Map.of("10", "3");
        given(postViewRepository.tryAcquireFlushLock(any())).willReturn(LOCK_TOKEN);
        given(postViewRepository.prepareProcessingBuffer()).willReturn(true);
        given(postViewRepository.getProcessingViewCounts()).willReturn(counts);
        willThrow(new IllegalStateException("db down")).given(postService).flushViewCountsToDB(counts);

        scheduler.flushViewCounts();

        verify(postViewRepository, never()).deleteProcessingBuffer();
        verify(postViewRepository).releaseFlushLock(LOCK_TOKEN);
    }
}
