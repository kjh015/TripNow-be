package com.traveler.post.domain.post.dto.result;

/**
 * 게시글 하나의 통계(슬롯 합계). 평균 별점은 저장하지 않고 합계로 계산한다.
 *
 * <p>{@code PostStatsRepository.summarizeAll}의 JPQL 생성자 표현식이 4개 인자 생성자를 쓴다.
 */
public record PostStatsSummary(Long postId, Long likeCount, Long commentCount, Long starSum, Double starAvg) {

    public PostStatsSummary(Long postId, Long likeCount, Long commentCount, Long starSum) {
        this(postId, likeCount, commentCount, starSum, average(starSum, commentCount));
    }

    /** 통계 행이 없을 때(조회 결과 없음) 쓰는 0 통계 */
    public static PostStatsSummary empty(Long postId) {
        return new PostStatsSummary(postId, 0L, 0L, 0L);
    }

    private static double average(Long starSum, Long commentCount) {
        return commentCount != null && commentCount > 0 ? (double) starSum / commentCount : 0.0;
    }
}
