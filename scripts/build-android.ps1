param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectRoot '.tools\jdk17'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $localJdk)) {
    $jdkDirectory = Get-ChildItem -LiteralPath $localJdk -Directory | Where-Object Name -Like 'jdk-*' | Select-Object -First 1
    if ($jdkDirectory) { $env:JAVA_HOME = $jdkDirectory.FullName }
}
if (-not $env:JAVA_HOME) { throw 'Install JDK 17 and set JAVA_HOME first.' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36\android.jar'))) {
    throw 'Install Android SDK platform 36 and build-tools 35.0.0, then set ANDROID_HOME.'
}
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
Push-Location (Join-Path $projectRoot 'android')
try {
    & '.\gradlew.bat' --no-daemon @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
