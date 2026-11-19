param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') { throw '배포 버전 형식이 올바르지 않습니다.' }
if (!$env:GH_REPO) { throw '배포할 GitHub 저장소를 GH_REPO에 지정하세요.' }

$tag = "v$Version"
$publication = Join-Path $PSScriptRoot '../outputs'
$zip = Join-Path $publication "MightyBot-$Version-AutoUpdate-Windows-x64.zip"
$manifest = Join-Path $publication 'bot-updates/latest.properties'
$engine = Join-Path $publication "bot-updates/bot-bridge-$Version.jar"
foreach ($asset in @($zip, $manifest, $engine)) {
    if (!(Test-Path -LiteralPath $asset -PathType Leaf)) { throw "배포 파일을 찾을 수 없습니다: $asset" }
}
$release = ConvertFrom-StringData ([IO.File]::ReadAllText($manifest))
$hash = (Get-FileHash -LiteralPath $engine -Algorithm SHA256).Hash.ToLowerInvariant()
if ($release.version -ne $Version -or $release.sha256 -ne $hash) {
    throw '배포 버전 또는 엔진 검증 정보가 일치하지 않습니다.'
}

# 이미 공개한 버전은 덮어쓰지 않습니다. 실패한 초안만 다시 올릴 수 있습니다.
$draft = & gh release view $tag --json isDraft --jq '.isDraft' 2>$null
if ($LASTEXITCODE -eq 0) {
    if ($draft.Trim() -ne 'true') { throw '이미 공개된 버전입니다. 새 버전 태그를 사용하세요.' }
} else {
    $notesDir = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { $env:TEMP }
    $notesPath = Join-Path $notesDir "mighty-release-$Version.txt"
    $notes = @"
마이티 테이블탑 봇 $Version

처음 사용할 때는 MightyBot-$Version-AutoUpdate-Windows-x64.zip 전체를 압축 해제한 뒤 MightyBot.exe를 실행하세요.
업데이트 주소가 설정된 기존 실행기는 다음 실행부터 새 엔진을 자동으로 받습니다.
Java가 포함된 Windows x64용 봇입니다. 맵과 직접 플레이 화면은 포함하지 않습니다.
"@
    [IO.File]::WriteAllText($notesPath, $notes, [Text.UTF8Encoding]::new($false))
    & gh release create $tag --verify-tag --draft --title "Mighty Bot $Version" --notes-file $notesPath
    if ($LASTEXITCODE -ne 0) { throw 'GitHub 배포 초안 생성 실패' }
}

# 모든 파일이 준비된 뒤 공개해야 최신 버전 주소가 완성된 배포만 가리킵니다.
& gh release upload $tag $zip $engine $manifest --clobber
if ($LASTEXITCODE -ne 0) { throw 'GitHub 배포 파일 업로드 실패' }
& gh release edit $tag --draft=false --latest
if ($LASTEXITCODE -ne 0) { throw 'GitHub 배포 공개 실패' }
