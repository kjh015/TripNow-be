// smoke: 스크립트와 API가 정상 동작하는지 확인한다 (수치는 보지 않는다).
// VU 1개가 1분 동안 조회·쓰기 API를 한 바퀴씩 돈다. 완료 기준: 에러율 0.
//
// 쓰기는 좋아요 추가 → 취소로 원상태를 만들고, 댓글은 1회 반복마다 1건 남는다.
import { group, sleep } from 'k6';

import {
    addLike,
    autocomplete,
    checkOk,
    createComment,
    getMyPosts,
    getPostDetail,
    removeLike,
    searchPosts,
} from '../lib/api.js';
import { loginAccounts } from '../lib/auth.js';
import { SUMMARY_TREND_STATS, strEnv } from '../lib/config.js';
import { KEYWORDS, pick, randomSearchParams } from '../lib/data.js';
import { setupReadMix } from '../lib/read-mix.js';

const NAMES = ['search', 'detail', 'autocomplete', 'my_posts', 'like_add', 'like_remove', 'comment_create'];

export const options = {
    scenarios: {
        smoke: { executor: 'constant-vus', vus: 1, duration: strEnv('DURATION', '1m') },
    },
    thresholds: Object.fromEntries([
        ['http_req_failed', ['rate==0']],
        ...NAMES.map((name) => [`http_req_failed{name:${name}}`, ['rate==0']]),
    ]),
    summaryTrendStats: SUMMARY_TREND_STATS,
};

export function setup() {
    return { ...setupReadMix(), tokens: loginAccounts(2) };
}

export default function (data) {
    const token = pick(data.tokens);
    const postId = pick(data.postIds);

    group('조회', () => {
        checkOk(searchPosts(randomSearchParams()), 'search');
        checkOk(getPostDetail(postId), 'detail');
        checkOk(autocomplete(pick(KEYWORDS)), 'autocomplete');
        checkOk(getMyPosts(token), 'my_posts');
    });

    group('쓰기', () => {
        checkOk(addLike(postId, token), 'like_add');
        checkOk(removeLike(postId, token), 'like_remove');
        checkOk(createComment(postId, 'smoke 테스트 댓글', 5, token), 'comment_create');
    });

    sleep(1);
}
