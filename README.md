<div align="center">

# TripNow

### 실시간 인기 여행지 공유 플랫폼

여행 경험을 공유하고, 사용자 행동 로그로 **지금 뜨는 여행지**를 집계합니다.

<br/>

<img src="https://img.shields.io/badge/Java%2021-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
<img src="https://img.shields.io/badge/Spring%20Boot%203.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.4">
<img src="https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
<img src="https://img.shields.io/badge/Elasticsearch-005571?style=for-the-badge&logo=elasticsearch&logoColor=white" alt="Elasticsearch">
<img src="https://img.shields.io/badge/AWS%20ECS-FF9900?style=for-the-badge&logo=amazonwebservices&logoColor=white" alt="AWS ECS">

<br/>

**MSA** · **CQRS** · **Event-Driven** · **Log Pipeline**

<br/>

[📝 리팩토링 일지](https://impossible-scraper-904.notion.site/316dd40f05d7806dae8be53c02820534?v=316dd40f05d78054a92b000ce82d3127&source=copy_link)

</div>

<br/>

---

## 📌 개요

> *"여행은 떠나고 싶은데, 어디로 가야 할지는 좀처럼 정해지지 않습니다."*

**TripNow**는 여행지를 고민하는 사람들의 선택을 돕기 위한 서비스입니다.

사용자가 남긴 여행 경험과 실시간 집계를 통해, **많은 사람들의 선택이 모이는 곳**을 실시간으로 보여줍니다.

<br/>

| 구분 | 내용 |
| :---: | :--- |
|  **과정** | 서경 SW아카데미 · 25-1학기 프로젝트 |
|  **주제** | 로그 데이터 수집을 통한 고객 데이터 활용 및 분석 |
|  **기간** | 초기 개발 `2025.04 ~ 2025.06` <br/> 리팩토링 `2026.03 ~` |

<br/>

---

## ✨ 주요 기능

### 실시간 사용자 로그 파이프라인

웹 서버에 쌓이는 방문 기록을 수집해 **포맷 처리 → 필터링 → 중복 제거 → 적재**의 4단계로 정제하고, Elasticsearch와 S3에 나눠 저장합니다.

### 실시간 인기 여행지 집계

좋아요 · 댓글 · 조회수 · 평점을 가중합한 **인기 점수**를 Elasticsearch에서 **10초 주기**로 집계해, 게시글 · 카테고리 · 지역별 실시간 순위를 산출합니다.

### 여행지 공유 커뮤니티

게시글 · 댓글 · 좋아요는 조회 성능과 쓰기 정합성을 위해 **CQRS**로 나뉘어, 쓰기는 `MySQL`, 읽기는 `Elasticsearch`가 담당합니다. 두 모델은 **Kafka 기반 Outbox 패턴**으로 동기화합니다.

<br/>

---

## 🏗️ 시스템 아키텍처

<img width="1790" height="921" alt="시스템 아키텍처" src="https://github.com/user-attachments/assets/6757790d-0c2e-4ee5-bd5d-a3f2dfb1dfe7" />

<br/>

### 📦 모듈 구성

| 모듈 | 역할 |
| :--- | :--- |
|  `api-gateway` | 라우팅 · 인증 필터 · 단일 진입점 |
|  `discovery-service` | Eureka 서비스 레지스트리 |
|  `web-api-service` | BFF — 여러 서비스 응답을 조립 |
|  `member-service` | 회원 가입 · 인증 |
|  `post-service` | 게시글 · 댓글 · 좋아요 **(쓰기 모델)** |
|  `search-service` | Elasticsearch 검색 · 조회 **(읽기 모델)** |
|  `user-activity-service` | 행동 로그 정제 · 인기도 집계 |
|  `common:core / api / db` | 공통 예외 · 응답 규격 · 엔티티 |

<br/>

### 🖥️ 앱 EC2 / 인프라 EC2 분리

#### 메모리 특성
 
> Kafka와 Elasticsearch는 대량의 메모리를 상시 점유하고, 특히 Elasticsearch는 스왑 메모리 사용 시 성능이 급락합니다. 반면 Spring 서비스는 7개의 JVM이 동시에 실행되며 부팅 시 발생하는 순간적인 메모리 사용량을 스왑 메모리로 커버할 수 있습니다.
>
> → **스왑을 피해야 하는 워크로드와 활용할 수 있는 워크로드를 별도의 EC2로 분리**했습니다.
 
#### 변경 주기
 
> Spring 서비스는 변경이 잦아 CI/CD와 배포 관리가 필요하지만, 인프라 서비스는 변경이 적어 자동화의 필요성이 낮습니다.
>
> → **앱 EC2는 ECS로, 인프라 EC2는 Docker Compose로** 관리하도록 분리했습니다.
 
#### 장애 분리
 
> 인프라 서비스는 ES 인덱스와 Kafka 오프셋 같은 상태를 보유합니다.
>
> → EC2를 분리하여 **Spring 서비스 배포가 상태 계층에 영향을 주지 않고**, 인프라 작업 중 발생한 문제도 API 가용성으로 전파되지 않게 합니다.

<br/>

### 🐳 ECS

앱 EC2 한 대를 컨테이너 인스턴스로 등록하고, **7개 서비스를 각각 ECS 서비스로 운영**합니다.

```
GitHub Actions  →  ECR 푸시  →  태스크 정의 갱신  →  배포
```

**자가 치유 · 배포 안전장치**
- 컨테이너가 죽으면 ECS가 재기동하고, 배포 중 헬스체크 실패 시 서킷 브레이커가 이전 버전으로 자동 롤백합니다.

**자원 관리**
- 서비스별 메모리 소프트 리밋을 선언하여 JVM 7개의 메모리 점유를 관리합니다.
  
**시크릿 관리**
- DB 비밀번호 등 민감한 환경 변수는 `.env` 대신 **SSM 파라미터 스토어**에 저장하고, 태스크 정의가 직접 참조합니다.

<br/>

---

##  🏗️ 데이터 흐름 아키텍처

### 📝 게시글 CQRS

<img width="855" height="442" alt="CQRS" src="https://github.com/user-attachments/assets/1af6eb22-5d4b-44e5-a186-f8cb5c110bb1" />

Post Service는 **MySQL에 원본을 저장**하고 변경 이벤트를 **Kafka로 발행**하며, Search Service가 이를 구독해 **Elasticsearch 인덱스를 갱신**합니다.

쓰기 트랜잭션과 이벤트 발행의 정합성은 **Outbox 패턴**으로 보장합니다.

<br/>

### 📈 사용자 행동 데이터 파이프라인

<img width="2102" height="472" alt="UserActivity" src="https://github.com/user-attachments/assets/30cc0d44-4a4f-44e2-9e6f-dc5cfc549f6d" />
 
**1. 수집** — 브라우저 Matomo Tracker의 추적 요청을 Nginx가 받고, Fluentd가 액세스 로그를 테일링해 Kafka로 발행
 
**2. 정제** — UserActivity Service가 `Format → Filter → Dedup → Sink` 단계로 로그를 정제, 정제 규칙은 MySQL에서 관리
 
**3. 저장** — Logstash가 정제 로그를 구독해 S3 아카이브 및 Elasticsearch 색인
 
**4. 서빙** — Kibana에서 조회 · 시각화

<br/>

---

## 🛠️ 기술 스택

**Frontend**

<img src="https://img.shields.io/badge/React-61DAFB?style=flat-square&logo=react&logoColor=black" alt="React"> <img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black" alt="JavaScript"> <img src="https://img.shields.io/badge/Vercel-000000?style=flat-square&logo=vercel&logoColor=white" alt="Vercel">

**Backend**

<img src="https://img.shields.io/badge/Java%2021-007396?style=flat-square&logo=openjdk&logoColor=white" alt="Java"> <img src="https://img.shields.io/badge/Spring%20Boot%203.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot"> <img src="https://img.shields.io/badge/Spring%20Cloud-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring Cloud"> <img src="https://img.shields.io/badge/Eureka%20%C2%B7%20Gateway%20%C2%B7%20OpenFeign-6DB33F?style=flat-square" alt="Eureka Gateway OpenFeign">

**Data Store**

<img src="https://img.shields.io/badge/MySQL%20(RDS)-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL"> <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white" alt="Redis"> <img src="https://img.shields.io/badge/Elasticsearch%208.15%20(nori)-005571?style=flat-square&logo=elasticsearch&logoColor=white" alt="Elasticsearch"> <img src="https://img.shields.io/badge/Amazon%20S3-569A31?style=flat-square&logo=amazons3&logoColor=white" alt="S3">

**Streaming · Analytics**

<img src="https://img.shields.io/badge/Apache%20Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white" alt="Kafka"> <img src="https://img.shields.io/badge/Fluentd-0E83C8?style=flat-square&logo=fluentd&logoColor=white" alt="Fluentd"> <img src="https://img.shields.io/badge/Logstash-005571?style=flat-square&logo=logstash&logoColor=white" alt="Logstash"> <img src="https://img.shields.io/badge/Kibana-005571?style=flat-square&logo=kibana&logoColor=white" alt="Kibana"> <img src="https://img.shields.io/badge/Matomo-3152A0?style=flat-square&logo=matomo&logoColor=white" alt="Matomo">

**Infra · CI/CD**

<img src="https://img.shields.io/badge/Docker%20Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose"> <img src="https://img.shields.io/badge/AWS%20ECS%20%C2%B7%20ECR%20%C2%B7%20ALB-FF9900?style=flat-square&logo=amazonwebservices&logoColor=white" alt="AWS"> <img src="https://img.shields.io/badge/Route%2053%20%C2%B7%20SSM-FF9900?style=flat-square&logo=amazonwebservices&logoColor=white" alt="Route53 SSM"> <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions">
