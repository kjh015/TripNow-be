# 프로세스 일시정지·재개 (Windows). fault-member-down의 hang 장애를 IDE로 띄운 서비스에 만들 때 쓴다.
# 프로세스를 재시작하지 않고 멈췄다가 이어서 실행한다. 멈춘 동안 그 서비스는 연결·요청에 응답하지 않는다.
#
# 사용 (PowerShell, 저장소 루트):
#   $memberPid = (Get-NetTCPConnection -LocalPort 8082 -State Listen).OwningProcess
#   powershell -ExecutionPolicy Bypass -File load-test/tools/suspend-process.ps1 suspend $memberPid
#   powershell -ExecutionPolicy Bypass -File load-test/tools/suspend-process.ps1 resume $memberPid
#
# Eureka 만료(기본 90초)를 넘기면 인스턴스가 목록에서 빠져 stop과 같은 상태가 되므로 90초 안에 재개한다.
param(
    [Parameter(Mandatory)][ValidateSet('suspend', 'resume')][string]$Action,
    [Parameter(Mandatory)][int]$ProcessId
)

$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class ProcessSuspender {
    [DllImport("ntdll.dll")] public static extern int NtSuspendProcess(IntPtr processHandle);
    [DllImport("ntdll.dll")] public static extern int NtResumeProcess(IntPtr processHandle);
}
"@

$process = Get-Process -Id $ProcessId
if ($Action -eq 'suspend') {
    $status = [ProcessSuspender]::NtSuspendProcess($process.Handle)
} else {
    $status = [ProcessSuspender]::NtResumeProcess($process.Handle)
}
if ($status -ne 0) {
    throw "$Action 실패: pid=$ProcessId status=$status"
}
Write-Output "$Action pid=$ProcessId ($($process.ProcessName)) $(Get-Date -Format 'HH:mm:ss')"
