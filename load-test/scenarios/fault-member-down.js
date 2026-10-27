// member-service 장애 시 조회 응답 시간. 서킷 브레이커 적용 전후 비교에 쓴다.
// search-read.js와 같은 트래픽·부하로 실행하고, member-service만 장애 상태로 둔다.
//
// 장애 방식(FAULT 환경 변수는 결과 기록용 태그다. 장애는 이 스크립트가 만들지 않는다)
//   hang: member-service가 응답하지 않는다. lt hang이 프로세스를 자동으로 일시정지·재개한다
//         (compose로 띄웠으면 lt hang -ManualFault 후 docker pause/unpause). Feign이 타임아웃까지 기다린다
//   stop: member-service 프로세스가 없다. IntelliJ Stop(또는 docker stop) 30초 뒤 lt stop. 연결이 바로 실패한다
//
// 볼 것: search·detail(member 닉네임 조회 포함)의 p95와 에러율, autocomplete(member 무관, 대조군)가 함께 느려지는지,
//        web-api 톰캣 바쁜 스레드 수(Grafana: tomcat_threads_busy_threads{application="web-api-service"})
import { strEnv, SUMMARY_TREND_STATS } from '../lib/config.js';
import { measureThresholds, readScenarios } from '../lib/load-profile.js';
import { READ_NAMES, readOnce, setupReadMix } from '../lib/read-mix.js';

const FAULT = strEnv('FAULT', 'hang');
if (!['hang', 'stop'].includes(FAULT)) {
    throw new Error(`FAULT는 hang 또는 stop: ${FAULT}`);
}

export const options = {
    scenarios: readScenarios('read'),
    thresholds: measureThresholds(READ_NAMES),
    summaryTrendStats: SUMMARY_TREND_STATS,
    tags: { fault: FAULT },
    // 장애 중에는 setup의 게시글 ID 수집도 느려질 수 있다
    setupTimeout: '2m',
};

export function setup() {
    console.log(`장애 방식: ${FAULT}. member-service가 장애 상태인지 확인하고 실행한다`);
    return setupReadMix();
}

export function read(data) {
    readOnce(data);
}
