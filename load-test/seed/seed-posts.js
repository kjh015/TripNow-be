// 테스트 게시글 생성: SEED_POSTS건(기본 2000)을 시드 회원들이 나눠 작성한다. 이미지는 넣지 않는다.
// DB에 직접 넣으면 Outbox를 타지 않아 ES에 반영되지 않으므로 반드시 API로 넣는다.
//
// 끝나면 teardown이 검색 API의 전체 건수(totalElements)를 다시 세어, 시작 전 건수 + 생성 건수와 같은지 확인한다.
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

import { countAllPosts, createPost } from '../lib/api.js';
import { loginAccounts } from '../lib/auth.js';
import { intEnv } from '../lib/config.js';
import { CATEGORIES, KEYWORDS, PLACES, pick } from '../lib/data.js';

const SEED_POSTS = intEnv('SEED_POSTS', 2000);
const created = new Counter('seed_posts_created');

export const options = {
    scenarios: {
        seed: {
            executor: 'shared-iterations',
            vus: intEnv('SEED_VUS', 10),
            iterations: SEED_POSTS,
            maxDuration: '30m',
        },
    },
    thresholds: { 'http_req_failed{name:create_post}': ['rate==0'] },
    // teardown이 ES 반영을 기다리는 시간 포함
    teardownTimeout: '3m',
};

export function setup() {
    const before = countAllPosts();
    if (before < 0) {
        throw new Error('검색 API로 게시글 수를 세지 못했다. 서비스 기동 상태를 확인한다');
    }
    console.log(`시작 전 검색 결과 ${before}건, ${SEED_POSTS}건 생성 시작`);
    return { before, tokens: loginAccounts() };
}

export default function (data) {
    const n = exec.scenario.iterationInTest;
    const place = pick(PLACES);
    const words = [pick(KEYWORDS), pick(KEYWORDS)];
    const res = createPost(
        {
            title: `${place.name} ${words[0]} 후기 #${n + 1}`,
            content:
                `${place.name}에 다녀왔습니다. ${words[0]}도 좋았고 ${words[1]}도 기억에 남습니다. ` +
                '부하테스트용으로 생성한 게시글입니다. '.repeat(1 + (n % 5)),
            travelPlace: place.name,
            address: place.address,
            category: pick(CATEGORIES),
            region: place.region,
            images: [],
        },
        data.tokens[n % data.tokens.length],
    );
    if (check(res, { 'create_post 2xx': (r) => r.status >= 200 && r.status < 300 })) {
        created.add(1);
    }
}

export function teardown(data) {
    // Outbox → Kafka → search-service 반영을 기다린다 (최대 150초)
    const expected = data.before + SEED_POSTS;
    let actual = -1;
    for (let i = 0; i < 30; i++) {
        actual = countAllPosts();
        if (actual >= expected) {
            break;
        }
        sleep(5);
    }
    console.log(`검색 결과 ${actual}건 / 기대 ${expected}건 (시작 전 ${data.before} + 생성 ${SEED_POSTS})`);
    check(actual, { '시드 건수 = 검색 결과 증가분': (v) => v === expected });
}
