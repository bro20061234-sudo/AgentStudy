param([switch]$CleanProfile)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$inputDir=Join-Path $workspace "target\native-input-$stamp"
$destination=Join-Path $workspace 'dist'
if(Test-Path -LiteralPath (Join-Path $destination 'SmartStudyAgent')){$destination=Join-Path $destination $stamp}
$appDir=Join-Path $destination 'SmartStudyAgent'
$candidates=@()
if($env:JAVA_HOME){$candidates+=Get-Item -LiteralPath $env:JAVA_HOME -ErrorAction SilentlyContinue}
$candidates+=Get-ChildItem -Path "$env:ProgramFiles\Java\jdk-*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
$jdk=$candidates | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'bin\jpackage.exe')} | Select-Object -First 1
if(!$jdk){throw 'Can JDK 21+ co jpackage. Dat JAVA_HOME toi thu muc JDK.'}
$jdkBin=Join-Path $jdk.FullName 'bin'
New-Item -ItemType Directory -Path $inputDir,$destination -Force | Out-Null
Push-Location $workspace
try{
    & .\build.bat '-B' '-ntp' '-DskipTests' package 'org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies' '-DincludeScope=runtime' "-DoutputDirectory=$inputDir"
    if($LASTEXITCODE -ne 0){throw 'Maven build failed'}
    Copy-Item -LiteralPath (Join-Path $workspace 'target\study-agent-1.0-SNAPSHOT.jar') -Destination $inputDir
    & (Join-Path $jdkBin 'jpackage.exe') --type app-image --name SmartStudyAgent --app-version 1.0.0 --vendor 'Study Agent' --description 'Smart Study Agent' --input $inputDir --dest $destination --main-jar 'study-agent-1.0-SNAPSHOT.jar' --main-class com.studyagent.DesktopLauncher --java-options '-Dfile.encoding=UTF-8' --java-options '-Dstudyagent.home=$APPDIR/..'
    if($LASTEXITCODE -ne 0){throw 'jpackage failed'}
    if(!$CleanProfile){
        $helpers=Join-Path $workspace "target\packaging-tools-$stamp"
        New-Item -ItemType Directory -Path $helpers | Out-Null
        & (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $helpers (Join-Path $PSScriptRoot 'ProfileSnapshot.java')
        if($LASTEXITCODE -ne 0){throw 'Profile helper compilation failed'}
        & (Join-Path $jdkBin 'java.exe') -cp "$helpers;$inputDir\*" ProfileSnapshot $workspace $appDir
        if($LASTEXITCODE -ne 0){throw 'Profile snapshot failed'}
    }
    if(!(Test-Path -LiteralPath (Join-Path $appDir 'config.properties'))){
        @('gemini.api.key=YOUR_GEMINI_API_KEY') | Set-Content -Encoding ASCII -LiteralPath (Join-Path $appDir 'config.properties')
    }
    Copy-Item -LiteralPath (Join-Path $workspace 'GOOGLE-CALENDAR.md') -Destination $appDir
    @'
SMART STUDY AGENT - WINDOWS

Mo SmartStudyAgent.exe de chay. Khong can cai Java hoac Maven.
Giu nguyen ca thu muc SmartStudyAgent (app va runtime la cac thanh phan can thiet).

Du lieu: study_agent.db va thu muc data.
Cau hinh AI: config.properties.
Nhat ky: data/logs/desktop.log.

Ban mac dinh di kem du lieu va cau hinh ca nhan hien tai.
De tao ban sach: powershell -ExecutionPolicy Bypass -File packaging/build-windows.ps1 -CleanProfile
'@ | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $appDir 'HUONG-DAN.txt')
    $nativeProcess=Start-Process -FilePath (Join-Path $appDir 'SmartStudyAgent.exe') -ArgumentList '--smoke-test' -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
    if(!$nativeProcess.WaitForExit(60000)){$nativeProcess.Kill();throw 'Native launch test timed out'}
    if($nativeProcess.ExitCode -ne 0){throw "Native launch failed ($($nativeProcess.ExitCode)). See data/logs/desktop.log"}
    $marker=Join-Path $appDir 'data\logs\native-smoke.ok'
    if(!(Test-Path -LiteralPath $marker)){throw 'Native launch verification marker missing'}
    $zip=Join-Path $destination 'SmartStudyAgent-Windows.zip'
    Compress-Archive -LiteralPath $appDir -DestinationPath $zip -CompressionLevel Optimal
    Write-Output "WINDOWS_EXE=$(Join-Path $appDir 'SmartStudyAgent.exe')"
    Write-Output "WINDOWS_ZIP=$zip"
    Write-Output "ZIP_SIZE_MB=$([Math]::Round((Get-Item -LiteralPath $zip).Length/1MB,1))"
}finally{Pop-Location}
