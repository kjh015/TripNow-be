# k6 실행 도우미 (Windows PowerShell). 긴 docker run 명령을 감싼다. 실제로 실행하는 명령은 화면에 먼저 출력한다.
#
# 사용: 저장소 루트의 lt.cmd로 실행한다(PowerShell은 .\lt, cmd는 lt). 설정 없이 쓸 수 있다. README 참고
#   lt warm               서비스를 (재)기동한 뒤 측정 전 2분 예열 (결과를 남기지 않는다)
#   lt smoke              1분 동작 확인
#   lt read               조회 응답 시간 (워밍업 1분 + 측정 5분)
#   lt write 2029         쓰기 경합. 두 번째 값은 대상 게시글 ID(좋아요·댓글이 없는 게시글)
#   lt hang               member 응답 없음. member-service를 자동으로 일시정지했다가 재개한다
#   lt stop               member 종료. IntelliJ에서 member-service를 먼저 Stop한다
#   lt seed-members / lt seed-posts
#   lt read -Vars RATE=30,DURATION=10m     환경 변수 덧붙이기
#
# 자동으로 하는 것: testid 태그(<이름>-<월일>-<시분>), Prometheus가 켜져 있으면 결과 전송, 요약 JSON 저장(results/raw/)
# lt.cmd 없이 직접: .\load-test\run.ps1 smoke (실행 정책 때문에 막히면 powershell -ExecutionPolicy Bypass -File .\load-test\run.ps1 smoke)
param(
    # 짧은 이름(smoke, read, write, hang, stop, seed-members, seed-posts), 긴 이름(search-read 등) 또는 스크립트 경로
    [Parameter(Mandatory = $true, Position = 0)][string]$Script,
    # 대상 게시글 ID (write에서 POST_ID로 넘긴다)
    [Parameter(Position = 1)][int]$Post = 0,
    # Grafana k6 대시보드의 testid. 주지 않으면 <이름>-<월일>-<시분>
    [string]$Tag,
    # 스크립트 환경 변수 KEY=VALUE. 여러 개는 쉼표로 잇는다: RATE=30,DURATION=5m
    # (powershell -File로 실행하면 인자가 문자열로만 넘어오므로 해시테이블 대신 문자열로 받는다)
    [string[]]$Vars = @(),
    # Prometheus로 결과를 보내지 않는다 (기본: Prometheus가 켜져 있으면 보낸다)
    [switch]$NoPrometheus,
    # hang에서 member-service를 자동으로 일시정지하지 않는다 (직접 장애를 만들 때)
    [switch]$ManualFault,
    # 요약 JSON(results/raw/)을 남기지 않는다
    [switch]$NoSummary,
    [string]$K6Image = 'grafana/k6:1.3.0',
    # 그 밖의 k6 옵션은 그대로 넘긴다. 예: --vus 2
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$K6Args
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$prometheusUrl = 'http://localhost:9091'
$memberPort = 8082

# 짧은 이름: 스크립트 경로 + 미리 넣을 환경 변수
$presets = @{
    'smoke'             = @{ Path = 'scenarios/smoke.js'; Vars = @() }
    # 서비스를 (재)기동한 뒤 본 테스트 측정 전 예열용 스크립트. 기동 직후 JVM은 수 배 느려 측정이 왜곡된다.
    'warm'              = @{ Path = 'scenarios/search-read.js'; Vars = @('WARMUP=10s', 'DURATION=2m') }
    'read'              = @{ Path = 'scenarios/search-read.js'; Vars = @() }
    'write'             = @{ Path = 'scenarios/write-contention.js'; Vars = @() }
    'hang'              = @{ Path = 'scenarios/fault-member-down.js'; Vars = @('FAULT=hang', 'WARMUP=30s', 'DURATION=60s') }
    'stop'              = @{ Path = 'scenarios/fault-member-down.js'; Vars = @('FAULT=stop', 'WARMUP=30s', 'DURATION=60s') }
    'seed-members'      = @{ Path = 'seed/seed-members.js'; Vars = @() }
    'seed-posts'        = @{ Path = 'seed/seed-posts.js'; Vars = @() }
    'search-read'       = @{ Path = 'scenarios/search-read.js'; Vars = @() }
    'write-contention'  = @{ Path = 'scenarios/write-contention.js'; Vars = @() }
    'fault-member-down' = @{ Path = 'scenarios/fault-member-down.js'; Vars = @() }
}
if ($Script -eq 'warm') {
    # 예열은 결과를 남기지도, 보내지도 않는다
    $NoSummary = $true
    $NoPrometheus = $true
}
if ($presets.ContainsKey($Script)) {
    $path = $presets[$Script].Path
    $presetVars = $presets[$Script].Vars
} else {
    $path = $Script -replace '\\', '/'
    $presetVars = @()
}
if (-not (Test-Path (Join-Path $root $path))) {
    throw "스크립트가 없다: $path (짧은 이름: smoke, read, write, hang, stop, seed-members, seed-posts)"
}
if (-not (Test-Path (Join-Path $root '.env'))) {
    throw "load-test/.env가 없다. .env.example을 복사해 SEED_PASSWORD를 채운다"
}

function Test-Url([string]$url) {
    try {
        Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 | Out-Null
        return $true
    } catch {
        return $false
    }
}

# PowerShell 5.1은 Stop 상태에서 외부 명령의 stderr를 오류로 바꿔 멈추므로, 확인하는 동안만 Continue로 둔다
$ErrorActionPreference = 'Continue'
docker version --format '{{.Server.Version}}' 2>$null | Out-Null
$dockerReady = $LASTEXITCODE -eq 0
$ErrorActionPreference = 'Stop'
if (-not $dockerReady) {
    throw 'Docker에 연결할 수 없다. Docker Desktop을 실행한 뒤 다시 시도한다'
}

# 장애 시나리오 준비
$memberPid = $null
if ($Script -eq 'hang' -and -not $ManualFault) {
    $listen = Get-NetTCPConnection -LocalPort $memberPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $listen) {
        throw "member-service($memberPort)가 실행 중이 아니다. 먼저 띄운다"
    }
    $memberProcess = Get-Process -Id $listen.OwningProcess
    # IDE로 띄운 java 프로세스만 멈춘다. compose로 띄웠으면 포트 주인이 Docker라서 멈추면 안 된다
    if ($memberProcess.ProcessName -ne 'java') {
        throw "포트 $memberPort 의 프로세스가 java가 아니다($($memberProcess.ProcessName)). compose로 띄웠다면 -ManualFault를 주고 docker pause member-service로 장애를 만든다"
    }
    $memberPid = $memberProcess.Id
}
if ($Script -eq 'stop' -and (Test-Url "http://localhost:$memberPort/actuator/health")) {
    throw 'member-service가 아직 실행 중이다. IntelliJ에서 Stop하고 30초 기다린 뒤 다시 실행한다'
}

if (-not $Tag) {
    $baseName = if ($presets.ContainsKey($Script)) { $Script } else { [IO.Path]::GetFileNameWithoutExtension($path) }
    $Tag = "$baseName-$(Get-Date -Format 'MMdd-HHmm')"
}

$dockerArgs = @('run', '--rm', '-i', '--env-file', (Join-Path $root '.env'), '-v', "${root}:/load-test", '-w', '/load-test', $K6Image, 'run')
if (-not $NoPrometheus) {
    if (Test-Url "$prometheusUrl/-/ready") {
        $dockerArgs += @('-o', 'experimental-prometheus-rw')
    } else {
        Write-Host "Prometheus($prometheusUrl)가 꺼져 있어 결과를 보내지 않는다 (docker/monitoring-compose에서 docker compose up -d)" -ForegroundColor Yellow
    }
}
$dockerArgs += @('--tag', "testid=$Tag")
if (-not $NoSummary) {
    # results/는 커밋하지 않아 새로 받은 저장소에는 없다. 폴더가 없으면 --summary-export가 실패하므로 새로 생성한다
    New-Item -ItemType Directory -Force -Path (Join-Path $root 'results/raw') | Out-Null
    $summary = "results/raw/$(Get-Date -Format 'yyyy-MM-dd-HHmmss')-$Tag.json"
    $dockerArgs += @('--summary-export', $summary)
}
$allVars = @($presetVars)
if ($Post -gt 0) {
    $allVars += "POST_ID=$Post"
}
# 사용자가 준 값이 프리셋 값보다 뒤에 오므로 같은 이름이면 사용자 값이 쓰인다
$allVars += (($Vars -join ',') -split ',')
foreach ($pair in $allVars) {
    if (-not $pair) { continue }
    if ($pair -notmatch '^[A-Za-z_][A-Za-z0-9_]*=') {
        throw "-Vars는 KEY=VALUE 형식이다: $pair"
    }
    $dockerArgs += @('-e', $pair)
}
if ($K6Args) {
    $dockerArgs += $K6Args
}
$dockerArgs += $path

Write-Host "> docker $($dockerArgs -join ' ')" -ForegroundColor DarkGray

# k6는 경고·진행 상황을 stderr로 쓴다. 출력을 받아 두는 환경에서 PowerShell 5.1이 이를 오류로 바꿔 멈추지 않게 한다
$ErrorActionPreference = 'Continue'
if ($memberPid) {
    # hang: k6 시작 5초 뒤(준비 단계가 끝난 뒤) member-service를 멈추고, 끝나면 반드시 재개한다
    $suspendTool = Join-Path $root 'tools/suspend-process.ps1'
    # Start-Process는 인자를 공백으로 이어 붙이기만 하므로, 공백이 있는 인자(경로, -Vars 값)는 따옴표로 감싸 한 인자로 넘긴다
    $procArgs = $dockerArgs | ForEach-Object { if ($_ -match '\s') { "`"$_`"" } else { $_ } }
    $k6 = Start-Process -FilePath 'docker' -ArgumentList $procArgs -NoNewWindow -PassThru
    # 핸들을 미리 잡아 두지 않으면 끝난 뒤 ExitCode가 비는 경우가 있다
    $null = $k6.Handle
    $suspended = $false
    try {
        Start-Sleep -Seconds 5
        & $suspendTool suspend $memberPid
        $suspended = $true
        $k6.WaitForExit()
    } finally {
        if ($suspended) {
            & $suspendTool resume $memberPid
        }
    }
    $code = $k6.ExitCode
} else {
    & docker @dockerArgs
    $code = $LASTEXITCODE
}

Write-Host "testid: $Tag (Grafana k6 대시보드의 testid에서 고른다)" -ForegroundColor DarkGray
if (-not $NoSummary -and (Test-Path (Join-Path $root $summary))) {
    Write-Host "요약: load-test/$summary" -ForegroundColor DarkGray
}
# k6 종료 코드: 0 = 통과, 99 = 기준(threshold) 초과, 그 밖 = 실행 오류
if ($Script -eq 'warm') {
    Write-Host '예열이 끝났다. 이제 측정한다 (예열 결과의 기준 초과는 무시해도 된다)' -ForegroundColor Green
    exit 0
}
if ($code -eq 99) {
    Write-Host '기준(threshold)을 넘었다. 장애 시나리오에서는 정상일 수 있다' -ForegroundColor Yellow
}
if ($Script -eq 'stop') {
    Write-Host '측정이 끝났다. IntelliJ에서 member-service를 다시 실행한다' -ForegroundColor Yellow
}
exit $code
