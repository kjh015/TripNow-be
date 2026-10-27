// TripNow API 호출 도우미. 모든 요청은 게이트웨이(BASE_URL)를 거친다.
// 요청마다 tags.name을 붙여 URL(게시글 ID 등)별로 지표가 쪼개지지 않게 한다.
import http from 'k6/http';
import { check } from 'k6';

import { BASE_URL } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function params(name, token) {
    const headers = { ...JSON_HEADERS };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    return { headers, tags: { name } };
}

export function get(path, name, token) {
    return http.get(`${BASE_URL}${path}`, params(name, token));
}

export function post(path, body, name, token) {
    return http.post(`${BASE_URL}${path}`, JSON.stringify(body), params(name, token));
}

export function del(path, name, token) {
    return http.del(`${BASE_URL}${path}`, null, params(name, token));
}

/** 공통 응답(ApiResponse)의 result. 본문이 JSON이 아니면 null */
export function result(res) {
    try {
        return res.json('result');
    } catch (e) {
        return null;
    }
}

export function checkOk(res, name) {
    return check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
}

export function query(params) {
    return Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
        .join('&');
}

// ── 조회 ──

export function searchPosts(params, token) {
    return get(`/api/v1/search/posts?${query(params)}`, 'search', token);
}

export function getPostDetail(postId, token) {
    return get(`/api/v1/search/posts/${postId}`, 'detail', token);
}

export function autocomplete(keyword) {
    return get(`/api/v1/search/posts/autocomplete?${query({ keyword })}`, 'autocomplete');
}

export function getMyPosts(token) {
    return get('/api/v1/search/posts/me?page=0&size=10', 'my_posts', token);
}

/** 검색 결과 전체 건수(totalElements). 실패하면 -1 */
export function countAllPosts() {
    const res = searchPosts({ page: 0, size: 1, sort: 'LATEST' });
    const body = result(res);
    return res.status === 200 && body ? body.totalElements : -1;
}

/** 최신 게시글 ID를 최대 limit개 모은다 (setup에서 한 번 호출) */
export function collectPostIds(limit) {
    const ids = [];
    const size = 100;
    for (let page = 0; ids.length < limit; page++) {
        const res = searchPosts({ page, size, sort: 'LATEST' });
        const body = result(res);
        if (res.status !== 200 || !body || body.content.length === 0) {
            break;
        }
        body.content.forEach((p) => ids.push(p.postId));
        if (body.isLast) {
            break;
        }
    }
    return ids.slice(0, limit);
}

// ── 쓰기 ──

export function createPost(body, token) {
    return post('/api/v1/posts', body, 'create_post', token);
}

export function addLike(postId, token) {
    return post('/api/v1/likes', { postId }, 'like_add', token);
}

export function removeLike(postId, token) {
    return del(`/api/v1/likes?postId=${postId}`, 'like_remove', token);
}

export function createComment(postId, content, star, token) {
    return post('/api/v1/comments', { postId, content, star }, 'comment_create', token);
}
