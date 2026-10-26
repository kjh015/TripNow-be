// 부하 모델. 요청 수가 서버 속도에 끌려가지 않도록 도착률 기반 실행기만 쓴다.
//
// MODE=constant (기본, 응답 시간 비교용)
//   warmup: RATE로 WARMUP 동안 (JVM 워밍업. 결과에서 제외)
//   measure: 이어서 RATE로 DURATION 동안. 비교 지표는 phase:measure 태그만 본다
// MODE=ramp (처리 한계 탐색용, 운영 사양 측정용)
//   measure: START_RATE에서 MAX_RATE까지 RAMP 동안 올린 뒤 HOLD 동안 유지
import { intEnv, strEnv } from './config.js';

export function readScenarios(exec) {
    const mode = strEnv('MODE', 'constant');
    const preAllocatedVUs = intEnv('PRE_VUS', 20);
    const maxVUs = intEnv('MAX_VUS', 200);

    if (mode === 'ramp') {
        return {
            measure: {
                executor: 'ramping-arrival-rate',
                exec,
                startRate: intEnv('START_RATE', 10),
                timeUnit: '1s',
                preAllocatedVUs,
                maxVUs,
                stages: [
                    { target: intEnv('MAX_RATE', 200), duration: strEnv('RAMP', '10m') },
                    { target: intEnv('MAX_RATE', 200), duration: strEnv('HOLD', '2m') },
                ],
                tags: { phase: 'measure' },
            },
        };
    }
    if (mode !== 'constant') {
        throw new Error(`MODE는 constant 또는 ramp: ${mode}`);
    }

    const rate = intEnv('RATE', 20);
    const warmup = strEnv('WARMUP', '1m');
    return {
        warmup: {
            executor: 'constant-arrival-rate',
            exec,
            rate,
            timeUnit: '1s',
            duration: warmup,
            preAllocatedVUs,
            maxVUs,
            tags: { phase: 'warmup' },
        },
        measure: {
            executor: 'constant-arrival-rate',
            exec,
            rate,
            timeUnit: '1s',
            duration: strEnv('DURATION', '5m'),
            startTime: warmup,
            preAllocatedVUs,
            maxVUs,
            tags: { phase: 'measure' },
        },
    };
}

/**
 * 요청 이름별 응답 시간 기준. 이름별 threshold가 있어야 k6 요약에 이름별 p50/p95/p99가 나온다.
 */
export function measureThresholds(names, p95Ms = intEnv('P95_MS', 500)) {
    const thresholds = {
        'http_req_failed{phase:measure}': ['rate<0.01'],
        'http_req_duration{phase:measure}': [`p(95)<${p95Ms}`],
    };
    names.forEach((name) => {
        thresholds[`http_req_duration{phase:measure,name:${name}}`] = [`p(95)<${p95Ms}`];
        thresholds[`http_req_failed{phase:measure,name:${name}}`] = ['rate<0.01'];
    });
    return thresholds;
}
