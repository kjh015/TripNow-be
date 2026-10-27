# load-test

k6 부하테스트 스크립트.

## 구성

```
load-test/
  lib/         config.js(환경 변수), api.js(API 호출), auth.js(로그인), data.js(검색어 등),
               load-profile.js(부하 모델·기준), read-mix.js(조회 트래픽 구성)
  seed/        seed-members.js, seed-posts.js
  scenarios/   smoke.js, search-read.js, write-contention.js, fault-member-down.js
  results/     측정 결과. 커밋하지 않는다(.gitignore). raw/에 k6 요약 JSON이 쌓인다
  tools/       suspend-process.ps1 (hang 장애용 프로세스 일시정지·재개, Windows)
  run.ps1      k6 실행 도우미 (docker 명령을 대신 만들어 실행)
../lt.cmd      저장소 루트의 단축 실행 파일 (run.ps1을 부른다)
```

| 시나리오 | 내용 | 보는 것 |
|---|---|---|
| `smoke.js` | VU 1개로 1분, 조회·쓰기 API 한 바퀴씩 | 스크립트·API 정상 동작 (에러율 0) |
| `search-read.js` | 검색 50% · 상세 30% · 자동완성 20% | API별 응답 시간 (p50/p95/p99) |
| `write-contention.js` | 인기글 1개에 좋아요 추가·취소 80% · 댓글 20% | 락 대기 시간, 409(락 경합) 건수, 끝난 뒤 좋아요 수 정합성(끝날 때 ES 값과 DB 확인 SQL을 출력) |
| `fault-member-down.js` | `search-read.js`와 같은 트래픽, member-service 장애 상태 | 장애가 조회 응답 시간·에러율에 주는 영향 (서킷 브레이커 전후) |

## 준비

1. **k6는 Docker 이미지로 실행한다.**
   ```powershell
   docker pull grafana/k6:1.3.0
   ```
2. **환경 변수 파일.** `.env.example`을 복사해 `.env`를 만들고 `SEED_PASSWORD`를 정한다.
   ```powershell
   copy load-test\.env.example load-test\.env
   ```
   - `BASE_URL`은 게이트웨이 주소다. k6가 컨테이너 안에서 돌기 때문에 `localhost` 대신 `host.docker.internal`을 쓴다.
   - 로컬이 아닌 주소는 `ALLOW_NON_LOCAL=true`가 없으면 실행을 거부한다.
3. **서비스 기동.** 인프라(MySQL·Redis·Kafka·ES)와 서비스 7개, 그리고 모니터링은 Grafana + Prometheus를 띄운다.

## 실행

### run.ps1로 실행 (권장)

`run.ps1`이 아래 docker 명령을 대신 만들어 실행한다.

**저장소 루트의 `lt.cmd`로 실행한다. 설정은 필요 없다.**

| 터미널 | 입력 |
|---|---|
| PowerShell (IntelliJ 터미널 포함) | `.\lt hang` |
| cmd | `lt hang` |

- `lt.cmd`가 실행 정책을 우회해(`-ExecutionPolicy Bypass`) `load-test/run.ps1`을 부른다.
- 아래 표의 `lt`는 PowerShell에서 `.\lt`로 읽는다.

| 명령 | 하는 일 |
|---|---|
| `lt warm` | **서비스를 (재)기동한 뒤 측정 전에 2분 예열.** 결과를 남기지도 보내지도 않는다 |
| `lt smoke` | 1분 동작 확인 |
| `lt read` | 조회 응답 시간 (워밍업 1분 + 측정 5분) |
| `lt write 2029` | 쓰기 경합. 두 번째 값은 대상 게시글 ID(좋아요·댓글이 없는 게시글) |
| `lt hang` | member 응답 없음 (워밍업 30초 + 측정 60초). k6 시작 5초 뒤 member-service를 자동으로 일시정지하고, 끝나면(중간에 멈춰도) 재개한다 |
| `lt stop` | member 종료 (워밍업 30초 + 측정 60초). IntelliJ에서 member-service를 먼저 Stop하고 30초 기다린 뒤 실행한다 |
| `lt seed-members`, `lt seed-posts` | 테스트 데이터 넣기 |

자동으로 하는 것:

- testid 태그: `<명령>-<월일>-<시분>` (예: `hang-1026-1530`). Grafana k6 대시보드의 `testid`에서 고른다
- Prometheus가 켜져 있으면 결과를 보낸다. 꺼져 있으면 알려 주고 보내지 않는다
- 요약 JSON 저장: `results/raw/<날짜시각>-<태그>.json`

| 옵션 | 설명 |
|---|---|
| `-Vars KEY=VALUE,...` | 스크립트 환경 변수(아래 "환경 변수" 표). 여러 개는 쉼표로 잇는다. 예: `lt read -Vars RATE=30,DURATION=10m` |
| `-Tag 이름` | testid를 직접 정한다 |
| `-NoPrometheus` | Prometheus로 보내지 않는다 |
| `-ManualFault` | `hang`에서 자동 일시정지를 하지 않는다(compose로 띄워 `docker pause`를 쓸 때) |
| `-NoSummary` | 요약 JSON을 남기지 않는다 |
| 그 밖 | k6 옵션은 뒤에 그대로 붙이면 넘어간다. 예: `--vus 2` |

- `hang`의 자동 일시정지는 IDE로 띄운 java 프로세스(포트 8082)에만 한다. 포트 주인이 java가 아니면(compose 등) 멈추지 않고 알려 준다.
- 긴 이름(`search-read`, `write-contention`, `fault-member-down`)과 스크립트 경로도 그대로 쓸 수 있다.
- `lt.cmd` 없이 실행: `.\load-test\run.ps1 hang`. 실행 정책 때문에 막히면 `powershell -ExecutionPolicy Bypass -File .\load-test\run.ps1 hang`.
- 종료 코드: 0 = 기준 통과, 99 = 기준(threshold) 초과(장애 시나리오에서는 정상일 수 있다), 그 밖 = 실행 오류.

### docker 명령으로 직접 실행

저장소 루트에서 PowerShell로 실행한다. 아래 `k6` 부분이 공통이다.

```powershell
docker run --rm -i --env-file load-test/.env -v "${PWD}/load-test:/load-test" -w /load-test grafana/k6:1.3.0 run <스크립트> [옵션]
```

| 순서 | 스크립트 | 예 |
|---|---|---|
| 1 | 회원 생성 (한 번) | `seed/seed-members.js` |
| 2 | 게시글 생성 (한 번) | `seed/seed-posts.js` (`-e SEED_POSTS=2000`) |
| 3 | smoke | `scenarios/smoke.js` |
| 4 | 측정 | `scenarios/search-read.js` 등 |

- 옵션은 `-e 이름=값`으로 준다. 예: `run -e RATE=30 -e DURATION=5m scenarios/search-read.js`
- 결과를 Grafana에서 보려면 `-o experimental-prometheus-rw`를 붙인다(주소는 `.env`의 `K6_PROMETHEUS_RW_SERVER_URL`).
- 요약 수치를 파일로 남기려면 `--summary-export results/raw/<파일>.json`을 붙인다. 폴더가 없으면 먼저 만든다(`run.ps1`은 자동으로 만든다). `results/`는 커밋되지 않는다.
- Git Bash에서 실행하면 `/load-test` 경로가 바뀌므로 앞에 `MSYS_NO_PATHCONV=1`을 붙인다.

### 환경 변수

| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | (필수) | 게이트웨이 주소 |
| `SEED_PASSWORD` | (필수) | 시드 계정 비밀번호. 8~20자, 영문·숫자·특수문자(`@$!%*#?&`) 포함 |
| `ACCOUNT_PREFIX` / `ACCOUNT_COUNT` | `loadtest` / `50` | 시드 계정 `loadtest0001` ~ `loadtest0050` |
| `SEED_POSTS` | `2000` | 생성할 게시글 수 |
| `MODE` | `constant` | `constant`(비교용) 또는 `ramp`(처리 한계 탐색) |
| `RATE` / `WARMUP` / `DURATION` | `20` / `1m` / `5m` | constant: 초당 요청 수, 워밍업(결과 제외), 측정 시간 |
| `START_RATE` / `MAX_RATE` / `RAMP` / `HOLD` | `10` / `200` / `10m` / `2m` | ramp: 시작·최대 초당 요청 수, 증가 시간, 최대 유지 시간 |
| `PRE_VUS` / `MAX_VUS` | `20` / `200` | 미리 만들 VU 수, 최대 VU 수 |
| `P95_MS` | `500` (쓰기 `1000`) | p95 기준(ms). 넘으면 threshold 실패로 표시 |
| `POST_ID` | 인기글 1위 | write-contention 대상 게시글 |
| `FAULT` | `hang` | fault-member-down의 장애 방식 기록용 태그 (`hang`/`stop`) |

## 결과 읽는 법

- 비교에 쓰는 값은 **`phase:measure`** 태그가 붙은 지표다. 워밍업 구간(`phase:warmup`)은 뺀다.
- 요약의 `http_req_duration{name:search,phase:measure}` 줄이 API별 응답 시간이다. `med`가 p50이다.
- `http_req_failed`는 실패 비율이다. 쓰기 시나리오의 409(락 경합)는 실패로 세고, `post_lock_conflicts`로 따로 센다. 한 건도 없으면 요약에 `post_lock_conflicts` 줄이 나오지 않는다.
- `dropped_iterations`가 있으면 정해진 요청 수를 다 보내지 못한 것이다. `MAX_VUS`를 늘리거나, 서버가 이미 한계라는 뜻이다.

### 구간별 시간 (Grafana, Prometheus)

k6는 게이트웨이까지의 전체 시간만 본다. 시간이 어느 서비스에서 쓰이는지는 서버 지표로 나눈다.

```promql
# 서비스·API별 p95 (web-api 전체 시간과 하위 서비스 시간을 나란히 본다)
histogram_quantile(0.95, sum by (application, uri, le) (rate(http_server_requests_seconds_bucket[1m])))
# web-api가 부른 하위 서비스(search·member)가 직접 잰 p95
# Feign 클라이언트 지표(http_client_requests)에는 Feign 호출이 기록되지 않아(Eureka 통신만, uri="none") 하위 서비스 쪽 지표로 본다
histogram_quantile(0.95, sum by (application, uri, le) (rate(http_server_requests_seconds_bucket{application=~"search-service|member-service", uri!~"/actuator.*"}[1m])))
# web-api 톰캣 바쁜 스레드 (장애 시 스레드가 묶이는지)
tomcat_threads_busy_threads{application="web-api-service"}
```

## 시드 데이터 확인

`seed-posts.js`는 끝날 때 검색 결과 건수가 "시작 전 + 생성 건수"와 같은지 확인한다. ES에서 직접 보려면 아래를 실행한다.

```powershell
curl.exe "http://localhost:9200/posts/_count"
```

## 운영 사양 측정

같은 VPC의 별도 EC2에서 실행하고, `MODE=ramp`로 초당 요청 수를 올리며 p95·에러율 기준이 깨지는 지점을 찾는다.
운영 DB·ES에 시드 데이터가 쌓이므로 계정 prefix를 따로 두고 측정 뒤 정리한다.
EC2에도 "준비"와 같이 k6 Docker 이미지와 `load-test/.env`를 두고, 저장소 루트에서 실행한다.

```bash
docker run --rm -i --env-file load-test/.env -v "${PWD}/load-test:/load-test" -w /load-test grafana/k6:1.3.0 run -e ALLOW_NON_LOCAL=true -e BASE_URL=<대상 주소> -e MODE=ramp -e MAX_RATE=200 scenarios/search-read.js
```
