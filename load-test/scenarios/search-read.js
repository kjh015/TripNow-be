// 조회 API 응답 시간 기준치.
// 트래픽 구성은 lib/read-mix.js, 부하 모델은 lib/load-profile.js.

import { SUMMARY_TREND_STATS } from '../lib/config.js';
import { measureThresholds, readScenarios } from '../lib/load-profile.js';
import { READ_NAMES, readOnce, setupReadMix } from '../lib/read-mix.js';

export const options = {
    scenarios: readScenarios('read'),
    thresholds: measureThresholds(READ_NAMES),
    summaryTrendStats: SUMMARY_TREND_STATS,
};

export function setup() {
    return setupReadMix();
}

export function read(data) {
    readOnce(data);
}
