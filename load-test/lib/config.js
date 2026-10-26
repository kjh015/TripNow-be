// 실행 설정. 대상 주소와 계정 정보는 환경 변수로만 받는다.

const LOCAL_HOSTS = ['localhost', '127.0.0.1', 'host.docker.internal'];

export function requireEnv(name) {
    const value = __ENV[name];
    if (!value) {
        throw new Error(`환경 변수 ${name}가 필요하다 (load-test/README.md 참고)`);
    }
    return value;
}

export function intEnv(name, defaultValue) {
    const value = __ENV[name];
    if (value === undefined || value === '') {
        return defaultValue;
    }
    const parsed = parseInt(value, 10);
    if (Number.isNaN(parsed)) {
        throw new Error(`환경 변수 ${name}는 정수여야 한다: ${value}`);
    }
    return parsed;
}

export function strEnv(name, defaultValue) {
    return __ENV[name] || defaultValue;
}

export const BASE_URL = requireEnv('BASE_URL').replace(/\/+$/, '');

// 로컬이 아닌 주소는 ALLOW_NON_LOCAL=true를 명시해야 실행한다
const host = BASE_URL.replace(/^https?:\/\//, '').split(/[:/]/)[0];
if (!LOCAL_HOSTS.includes(host) && __ENV.ALLOW_NON_LOCAL !== 'true') {
    throw new Error(`로컬 주소가 아니다: ${BASE_URL}. 운영 환경이면 ALLOW_NON_LOCAL=true를 함께 준다`);
}

// 시드 계정: <ACCOUNT_PREFIX>0001 ~ <ACCOUNT_PREFIX>NNNN. 비밀번호는 load-test/.env의 SEED_PASSWORD
export const ACCOUNT_PREFIX = strEnv('ACCOUNT_PREFIX', 'loadtest');
export const ACCOUNT_COUNT = intEnv('ACCOUNT_COUNT', 50);

if (!/^[a-zA-Z0-9]{1,16}$/.test(ACCOUNT_PREFIX)) {
    throw new Error('ACCOUNT_PREFIX는 영문·숫자 16자 이하여야 한다 (로그인 ID 규칙: 영문·숫자 4~20자)');
}

// 요약 출력에 쓰는 통계. 응답 시간 비교의 기준은 p50·p95·p99다
export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'];
