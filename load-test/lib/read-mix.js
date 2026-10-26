// 조회 트래픽 구성. search-read.js와 fault-member-down.js가 같은 구성을 써야 장애 전후를 비교할 수 있다.
//
//   search        50%  검색(키워드·카테고리·지역·정렬 조합). web-api → search-service + member-service(닉네임)
//   detail        30%  상세. web-api → search-service + member-service(닉네임)
//   autocomplete  20%  자동완성. web-api → search-service만 (member 장애와 무관한 대조군)

import { autocomplete, checkOk, collectPostIds, countAllPosts, getPostDetail, searchPosts } from './api.js';
import { intEnv } from './config.js';
import { KEYWORDS, pick, randomSearchParams } from './data.js';

export const READ_NAMES = ['search', 'detail', 'autocomplete'];

export function setupReadMix() {
    if (countAllPosts() < 0) {
        throw new Error('검색 API 호출이 실패했다. BASE_URL과 서비스 기동 상태를 확인한다');
    }
    const postIds = collectPostIds(intEnv('POST_ID_LIMIT', 500));
    if (postIds.length === 0) {
        throw new Error('게시글이 없다. seed/seed-posts.js를 먼저 실행한다');
    }
    return { postIds };
}

export function readOnce(data) {
    const roll = Math.random();
    if (roll < 0.5) {
        checkOk(searchPosts(randomSearchParams()), 'search');
    } else if (roll < 0.8) {
        checkOk(getPostDetail(pick(data.postIds)), 'detail');
    } else {
        checkOk(autocomplete(pick(KEYWORDS)), 'autocomplete');
    }
}
