// 테스트 회원 생성: <ACCOUNT_PREFIX>0001 ~ <ACCOUNT_PREFIX><ACCOUNT_COUNT>
// 비밀번호는 모두 SEED_PASSWORD(load-test/.env). 계정 정보는 화면에 출력하지 않는다.
// 이미 있는 계정(409)은 건너뛰므로 여러 번 실행해도 된다.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import { post } from '../lib/api.js';
import { accountId, seedPassword } from '../lib/auth.js';
import { ACCOUNT_COUNT, ACCOUNT_PREFIX, intEnv } from '../lib/config.js';

const created = new Counter('seed_members_created');
const skipped = new Counter('seed_members_skipped');

// 409(이미 있는 회원)는 실패로 세지 않는다
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

export const options = {
    scenarios: {
        seed: {
            executor: 'shared-iterations',
            vus: intEnv('SEED_VUS', 5),
            iterations: ACCOUNT_COUNT,
            maxDuration: '10m',
        },
    },
    thresholds: { http_req_failed: ['rate==0'] },
};

export function setup() {
    seedPassword(); // 시작 전에 비밀번호 규칙 확인
    console.log(`회원 ${ACCOUNT_COUNT}명 생성 시작 (prefix=${ACCOUNT_PREFIX})`);
}

export default function () {
    const i = exec.scenario.iterationInTest + 1;
    const loginId = accountId(i);
    const res = post(
        '/api/v1/members',
        {
            loginId,
            password: seedPassword(),
            email: `${loginId}@loadtest.invalid`,
            // 닉네임도 유일해야 하므로 로그인 ID를 그대로 쓴다
            nickname: loginId,
            birthDate: '1995-01-01',
            gender: 'NONE',
        },
        'signup',
    );

    if (res.status === 409) {
        skipped.add(1);
        return;
    }
    if (check(res, { 'signup 2xx': (r) => r.status >= 200 && r.status < 300 })) {
        created.add(1);
    }
}
