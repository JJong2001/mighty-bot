$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkCandidates = @()
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$javaCompiler = Get-Command javac -ErrorAction SilentlyContinue
if ($javaCompiler) { $jdkCandidates += Split-Path (Split-Path $javaCompiler.Source -Parent) -Parent }
$jdkFolder = Join-Path $env:USERPROFILE '.jdks'
if (Test-Path -LiteralPath $jdkFolder) { $jdkCandidates += Get-ChildItem -LiteralPath $jdkFolder -Directory | Select-Object -ExpandProperty FullName }
$jdk = $jdkCandidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'JDK 17+ required. Set JAVA_HOME to your JDK directory.' }
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $jdk
    & (Join-Path $PSScriptRoot 'gradlew.bat') --no-daemon --console=plain build
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build or JUnit tests failed. See build\reports\tests\test\index.html' }
    Write-Host ('Built ' + (Get-Content -LiteralPath 'build\active-jar.txt' -Raw).Trim())
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
