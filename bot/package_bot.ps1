param([string]$Jdk = $env:JAVA_HOME, [string]$ManifestUrl = $env:MIGHTY_UPDATE_MANIFEST, [switch]$UseBuiltJars)
$ErrorActionPreference = 'Stop'
if (!$Jdk) { throw '빌드에 사용할 JDK 경로를 -Jdk로 지정하세요.' }
# 배포 주소는 코드와 함께 관리합니다. 별도 주소를 지정하면 그 주소를 우선합니다.
$updateSource = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'update-source.properties')))
if (!$ManifestUrl) { $ManifestUrl = $updateSource.manifestUrl }
$engine = Join-Path $PSScriptRoot '../engine'
if (!$UseBuiltJars) {
    $env:JAVA_HOME = $Jdk
    $env:MIGHTY_UPDATE_MANIFEST = $ManifestUrl
    & (Join-Path $engine 'gradlew.bat') -p $engine --console=plain :releaseBot
    exit $LASTEXITCODE
}
$jar = Join-Path $PSScriptRoot 'build/libs/bot-bridge.jar'
$versionSource = Join-Path $PSScriptRoot '../engine/src/mighty/ApplicationInfo.java'
$version = [regex]::Match([IO.File]::ReadAllText((Resolve-Path $versionSource)), 'VERSION\s*=\s*"([0-9.]+)"').Groups[1].Value
if (!$version) { throw '엔진 버전을 찾을 수 없습니다.' }
$publication = Join-Path $PSScriptRoot '../outputs'
$zip = Join-Path $publication "MightyBot-$version-AutoUpdate-Windows-x64.zip"
$stage = Join-Path $env:TEMP ('MightyBot-' + [Guid]::NewGuid())
$inputDir = Join-Path $stage 'input'
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
if ($ManifestUrl -and (![Uri]::IsWellFormedUriString($ManifestUrl, [UriKind]::Absolute) -or ([Uri]$ManifestUrl).Scheme -ne 'https')) {
    throw '배포 주소는 HTTPS 주소여야 합니다.'
}
$feed = Join-Path $publication 'bot-updates'
New-Item -ItemType Directory -Path $feed -Force | Out-Null
$asset = "bot-bridge-$version.jar"
Copy-Item -LiteralPath $jar -Destination (Join-Path $feed $asset) -Force
$hash = (Get-FileHash (Join-Path $feed $asset) -Algorithm SHA256).Hash.ToLowerInvariant()
# 버전 안내 확인과 다운로드 사이에 최신 배포가 바뀌어도 같은 버전의 파일을 받습니다.
$assetReference = $asset
if ($ManifestUrl -eq $updateSource.manifestUrl) {
    $assetReference = "$($updateSource.releaseBaseUrl)/v$version/$asset"
}
@("version=$version", "jar=$assetReference", "sha256=$hash") | Set-Content -Encoding ASCII (Join-Path $feed 'latest.properties')
Copy-Item -LiteralPath $jar -Destination $inputDir
@("bundledVersion=$version", "manifestUrl=$ManifestUrl") | Set-Content -Encoding ASCII (Join-Path $inputDir 'updates.properties')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'launcher/build/libs/bot-launcher.jar') -Destination $inputDir
$runtime = Join-Path $stage 'linked-runtime'
& (Join-Path $Jdk 'bin/jlink.exe') --add-modules java.base,java.net.http,jdk.httpserver,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --output $runtime
if ($LASTEXITCODE -ne 0) { throw '내장 자바 구성 실패' }
& (Join-Path $Jdk 'bin/jpackage.exe') --type app-image --dest $stage --input $inputDir --name MightyBot --app-version $version --main-jar bot-launcher.jar --main-class mighty.launcher.BotLauncher --runtime-image $runtime --java-options '-Dfile.encoding=UTF-8' --win-console
if ($LASTEXITCODE -ne 0) { throw 'Windows 실행기 패키징 실패' }
$app = Join-Path $stage 'MightyBot'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'record_settings.cmd') -Destination $app
@"
마이티 테이블탑 봇 $version

1. ZIP 전체를 압축 해제합니다. MightyBot.exe와 app, runtime 폴더를 함께 둡니다.
2. MightyBot.exe를 실행합니다. 준비 완료 메시지가 보이면 창을 열어 둡니다.
3. 테이블탑 시뮬레이터에서 마이티 맵의 연결 확인을 누르고 봇을 사용합니다.
4. 봇을 종료하려면 실행 창을 닫습니다.

기록을 사용하려면 record_settings.cmd에서 복기 또는 점수 기록을 켜세요. 새 마이티 맵이 필요합니다.
점수 CSV와 복기 JSON은 %LOCALAPPDATA%\MightyBot\records에서 확인할 수 있습니다.

Windows x64용입니다. 자바는 실행기에 포함되어 있습니다.
배포 주소가 설정되어 있으면 실행할 때 엔진을 확인합니다. 업데이트할 수 없으면 기존 버전으로 실행합니다.
이미 봇이 켜져 있다면 기존 실행기를 종료한 뒤 새 실행기를 켜세요.
"@ | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $app '사용법.txt')
Compress-Archive -Path $app -DestinationPath $zip -Force
Get-Item -LiteralPath $zip




