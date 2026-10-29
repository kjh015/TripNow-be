package com.traveler.post.domain.post.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.traveler.post.domain.post.dto.result.PostStatsSyncBatch;
import com.traveler.post.domain.post.repository.PostStatsSyncRepository;
import com.traveler.post.domain.post.service.PostService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostStatsSyncSchedulerTest {

    private static final String LOCK_TOKEN = "lock-token";

    @Mock
    private PostStatsSyncRepository postStatsSyncRepository;

    @Mock
    private PostService postService;

    private SimpleMeterRegistry meterRegistry;

    private PostStatsSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new PostStatsSyncScheduler(postStatsSyncRepository, postService, meterRegistry);
    }

    @Test
    @DisplayName("락을 얻지 못하면 버퍼를 건드리지 않고 건너뛴다")
    void skipsWhenLockNotAcquired() {
        given(postStatsSyncRepository.tryAcquireSyncLock(any())).willReturn(null);

        scheduler.sync();

        verify(postStatsSyncRepository, never()).prepareProcessing();
        verify(postStatsSyncRepository, never()).releaseSyncLock(any());
        verifyNoInteractions(postService);
    }

    @Test
    @DisplayName("처리할 조회수와 통계 변경이 없으면 동기화 없이 락만 해제한다")
    void releasesLockWhenNothingToSync() {
        given(postStatsSyncRepository.tryAcquireSyncLock(any())).willReturn(LOCK_TOKEN);
        given(postStatsSyncRepository.prepareProcessing()).willReturn(false);

        scheduler.sync();

        verifyNoInteractions(postService);
        verify(postStatsSyncRepository).releaseSyncLock(LOCK_TOKEN);
    }

    @Test
    @DisplayName("동기화에 성공하면 처리용 키를 지우고 락을 해제한다")
    void deletesProcessingAfterSuccessfulSync() {
        PostStatsSyncBatch batch = new PostStatsSyncBatch(Map.of(10L, 3L), Set.of(20L));
        given(postStatsSyncRepository.tryAcquireSyncLock(any())).willReturn(LOCK_TOKEN);
        given(postStatsSyncRepository.prepareProcessing()).willReturn(true);
        given(postStatsSyncRepository.getProcessing()).willReturn(batch);

        scheduler.sync();

        verify(postService).syncStats(batch);
        verify(postStatsSyncRepository).deleteProcessing();
        verify(postStatsSyncRepository).releaseSyncLock(LOCK_TOKEN);
        assertThat(syncedPosts()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("동기화가 실패하면 처리용 키를 보존해 다음 주기에 재시도하고, 락은 해제한다")
    void keepsProcessingWhenSyncFails() {
        PostStatsSyncBatch batch = new PostStatsSyncBatch(Map.of(10L, 3L), Set.of(20L));
        given(postStatsSyncRepository.tryAcquireSyncLock(any())).willReturn(LOCK_TOKEN);
        given(postStatsSyncRepository.prepareProcessing()).willReturn(true);
        given(postStatsSyncRepository.getProcessing()).willReturn(batch);
        willThrow(new IllegalStateException("db down")).given(postService).syncStats(batch);

        scheduler.sync();

        verify(postStatsSyncRepository, never()).deleteProcessing();
        verify(postStatsSyncRepository).releaseSyncLock(LOCK_TOKEN);
        assertThat(syncedPosts()).isZero();
    }

    @Test
    @DisplayName("동기화 묶음의 발행 대상은 조회수가 늘었거나 통계가 바뀐 게시글이다")
    void batchPostIds() {
        PostStatsSyncBatch batch = new PostStatsSyncBatch(Map.of(10L, 3L, 20L, 1L), Set.of(20L, 30L));

        assertThat(batch.postIds()).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    private double syncedPosts() {
        return meterRegistry.get("post.view.sync.posts").counter().count();
    }
}
