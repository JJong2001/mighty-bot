param([switch]$BuildOnly)

$ErrorActionPreference = 'Stop'
$engine = Join-Path $PSScriptRoot '../engine'
$candidates = @($env:JAVA_HOME)
$localJdks = Join-Path $env:USERPROFILE '.jdks'
if (Test-Path $localJdks) {
    $candidates += Get-ChildItem -LiteralPath $localJdks -Directory | Select-Object -ExpandProperty FullName
}
$jdk = $candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin/javac.exe')) } | Select-Object -First 1
if (!$jdk) { throw '빌드에 사용할 JDK 경로를 JAVA_HOME에 지정하세요.' }
$env:JAVA_HOME = $jdk
& (Join-Path $engine 'gradlew.bat') -p $engine --console=plain :bridge:botJar
if ($LASTEXITCODE -ne 0) { throw '연결 프로그램 빌드 실패' }
if (!$BuildOnly) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'build/libs/bot-bridge.jar') -Destination (Join-Path $PSScriptRoot 'bot-bridge.jar') -Force
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'jdk_path.txt'), $jdk)
Write-Output 'Gradle 엔진 JAR 기반 연결 프로그램 빌드 완료'
