$ErrorActionPreference='Stop'
$pidPath=Join-Path $PSScriptRoot 'service_pid.txt'
if (!(Test-Path -LiteralPath $pidPath)) { Write-Output '실행 기록이 없습니다.';exit 0 }
$servicePid=[int]([IO.File]::ReadAllText($pidPath))
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$servicePid" -ErrorAction SilentlyContinue
$jar=Join-Path $PSScriptRoot 'bot-bridge.jar'
if ($process -and $process.CommandLine -and $process.CommandLine.Contains($jar)) { Stop-Process -Id $servicePid;Write-Output 'BOT 연결 프로그램 종료' }
