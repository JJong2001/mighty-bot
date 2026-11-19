$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$engineRoot=Join-Path (Split-Path $root -Parent) 'engine'
$source=[IO.File]::ReadAllText((Join-Path $engineRoot 'src\mighty\ApplicationInfo.java'))
$expectedVersion=[regex]::Match($source,'VERSION\s*=\s*"([0-9.]+)"').Groups[1].Value
try {
 $health=Invoke-RestMethod -Uri 'http://127.0.0.1:18743/health' -TimeoutSec 2
 if ($health.service -eq 'mighty-bot-bridge') {
  if ($health.engineVersion -eq $expectedVersion -and $health.policy -eq 'rule') {
   Write-Output '현재 AI 정책의 BOT 연결 프로그램이 이미 실행 중입니다.';exit 0
  }
  & (Join-Path $root 'stop_bridge.ps1')
 } else { throw '다른 프로그램이 연결 포트를 사용하고 있습니다.' }
} catch { if ($_.Exception.Message -like '다른 프로그램*') { throw } }
& (Join-Path $root 'build_bridge.ps1')
$jdk=[IO.File]::ReadAllText((Join-Path $root 'jdk_path.txt')).Trim()
$java=Join-Path $jdk 'bin\java.exe'
$jar=Join-Path $root 'bot-bridge.jar'
$process=Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8','--add-modules','jdk.httpserver','-jar',('"'+$jar+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'service.log') -RedirectStandardError (Join-Path $root 'service-error.log')
[IO.File]::WriteAllText((Join-Path $root 'service_pid.txt'),[string]$process.Id)
for ($attempt=0;$attempt -lt 20;$attempt++) {
 Start-Sleep -Milliseconds 250
 try { $health=Invoke-RestMethod -Uri 'http://127.0.0.1:18743/health' -TimeoutSec 1;if($health.service -eq 'mighty-bot-bridge'){Write-Output 'BOT 연결 준비 완료';exit 0} } catch {}
}
throw '연결 프로그램 시작 실패. bot 폴더의 service-error.log를 확인하세요.'
