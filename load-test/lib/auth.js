// 시드 계정 로그인. 액세스 토큰은 로그인 응답의 Authorization 헤더로 온다(AuthHttpSupport).
// 토큰은 setup()에서 한 번 받아 VU들이 나눠 쓴다. 액세스 토큰 만료가 1시간이라 시나리오는 1시간 안에 끝나게 둔다.
import http from 'k6/http';

import { ACCOUNT_COUNT, ACCOUNT_PREFIX, BASE_URL, requireEnv } from './config.js';

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,20}$/;

export function seedPassword() {
    const password = requireEnv('SEED_PASSWORD');
    if (!PASSWORD_RULE.test(password)) {
        throw new Error('SEED_PASSWORD는 8~20자, 영문·숫자·특수문자(@$!%*#?&)를 모두 포함해야 한다');
    }
    return password;
}

/** i번째 시드 계정의 로그인 ID (1부터) */
export function accountId(i) {
    return `${ACCOUNT_PREFIX}${String(i).padStart(4, '0')}`;
}

/** 로그인해서 액세스 토큰을 돌려준다. 실패하면 null */
export function login(loginId, password) {
    const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ loginId, password }), {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'login' },
    });
    const header = res.headers.Authorization;
    if (res.status !== 200 || !header || !header.startsWith('Bearer ')) {
        return null;
    }
    return header.substring('Bearer '.length);
}

/** 시드 계정 count개로 로그인한다. 하나도 성공하지 못하면 테스트를 멈춘다 */
export function loginAccounts(count = ACCOUNT_COUNT) {
    const password = seedPassword();
    const tokens = [];
    for (let i = 1; i <= count; i++) {
        const token = login(accountId(i), password);
        if (token) {
            tokens.push(token);
        }
    }
    if (tokens.length === 0) {
        throw new Error('시드 계정 로그인이 모두 실패했다. seed/seed-members.js를 먼저 실행한다');
    }
    if (tokens.length < count) {
        console.warn(`시드 계정 로그인 ${tokens.length}/${count}건 성공`);
    }
    return tokens;
}
