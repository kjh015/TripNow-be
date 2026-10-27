// 인기글 1개에 쓰기를 몰아 비관적 락(PostRepository.findByIdWithLock, lock timeout 3s) 대기를 본다.
//   80%: 좋아요 추가 → 바로 취소 (같은 사용자)
//   20%: 댓글 작성
// 부하 모델은 lib/load-profile.js (RATE 기본 20/s).
//
// 볼 것: like_add·like_remove·comment_create의 p95, 락 경합 응답(409 POST_LOCK_CONFLICT) 건수,
//        Outbox 대기 건수(outbox_messages)와 search-service 컨슈머 lag이 0으로 돌아오는 시간
// 끝난 뒤 정합성: DB post.like_count = likes 행 수 = ES likeCount. teardown이 ES 값과, 같이 실행할 DB 확인 SQL을 출력한다
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

import { addLike, checkOk, createComment, getPostDetail, removeLike, result, searchPosts } from '../lib/api.js';
import { loginAccounts } from '../lib/auth.js';
import { intEnv, SUMMARY_TREND_STATS } from '../lib/config.js';
import { measureThresholds, readScenarios } from '../lib/load-profile.js';

const WRITE_NAMES = ['like_add', 'like_remove', 'comment_create'];
const lockConflicts = new Counter('post_lock_conflicts');

export const options = {
    scenarios: readScenarios('write'),
    thresholds: measureThresholds(WRITE_NAMES, intEnv('P95_MS', 1000)),
    summaryTrendStats: SUMMARY_TREND_STATS,
    teardownTimeout: '2m',
};

export function setup() {
    let postId = intEnv('POST_ID', 0);
    if (!postId) {
        const top = result(searchPosts({ page: 0, size: 1, sort: 'POPULAR' }));
        if (!top || top.content.length === 0) {
            throw new Error('게시글이 없다. seed/seed-posts.js를 먼저 실행한다');
        }
        postId = top.content[0].postId;
    }
    const before = result(getPostDetail(postId));
    console.log(`대상 게시글 ${postId}: 시작 전 likeCount=${before && before.likeCount}, commentCount=${before && before.commentCount}`);
    return { postId, tokens: loginAccounts() };
}

function countConflict(res) {
    if (res.status === 409) {
        lockConflicts.add(1);
    }
    return res;
}

export function write(data) {
    // 같은 사용자의 요청이 동시에 겹치지 않도록 VU마다 계정을 고정한다 (VU 수가 계정 수보다 많으면 겹친다)
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (Math.random() < 0.8) {
        checkOk(countConflict(addLike(data.postId, token)), 'like_add');
        checkOk(countConflict(removeLike(data.postId, token)), 'like_remove');
    } else {
        checkOk(countConflict(createComment(data.postId, '부하테스트 댓글', 1 + Math.floor(Math.random() * 5), token)), 'comment_create');
    }
}

export function teardown(data) {
    // ES 반영(Outbox → Kafka → search-service)을 기다린 뒤 값을 출력한다
    sleep(intEnv('SETTLE_SECONDS', 30));
    const after = result(getPostDetail(data.postId));
    console.log(`대상 게시글 ${data.postId}: ES likeCount=${after && after.likeCount}, commentCount=${after && after.commentCount}`);
    // k6는 DB에 접속할 수 없으므로 확인용 SQL을 대상 게시글 ID까지 채워 출력한다.
    // like_count = like_rows = ES likeCount, comment_count = comment_rows = ES commentCount이면 정합성이 맞다
    console.log(`DB와 비교 (post-service DB에서 실행): ${consistencySql(data.postId)}`);
}

function consistencySql(postId) {
    return (
        'SELECT p.like_count, ' +
        '(SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_rows, ' +
        'p.comment_count, ' +
        '(SELECT COUNT(*) FROM comment c WHERE c.post_id = p.id AND c.is_deleted = false) AS comment_rows ' +
        `FROM post p WHERE p.id = ${postId};`
    );
}
