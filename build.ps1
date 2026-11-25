# Build edgegest.dex và (tuỳ chọn) đẩy lên điện thoại.
#   .\build.ps1            -> build
#   .\build.ps1 -Push      -> build + adb push vào /data/local/tmp/edgegest
param([switch]$Push)
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$androidJar = "$sdk\platforms\android-35\android.jar"
$d8 = "$sdk\build-tools\34.0.0\d8.bat"
$out = "$root\out"

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes" | Out-Null

$srcs = Get-ChildItem -Recurse "$root\src" -Filter *.java | ForEach-Object { $_.FullName }
& javac -source 8 -target 8 -bootclasspath $androidJar -Xlint:-options -encoding UTF-8 -d "$out\classes" @srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --release --min-api 30 --lib $androidJar --output $out @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
Move-Item "$out\classes.dex" "$out\edgegest.dex" -Force
Write-Host "Built: $out\edgegest.dex"

if ($Push) {
    adb shell mkdir -p /data/local/tmp/edgegest
    adb push "$out\edgegest.dex" /data/local/tmp/edgegest/edgegest.dex
    adb push "$root\device\start.sh" /data/local/tmp/edgegest/start.sh
    adb push "$root\device\start-server.sh" /data/local/tmp/edgegest/start-server.sh
    adb push "$root\device\stop.sh" /data/local/tmp/edgegest/stop.sh
    adb shell "sed -i 's/\r$//' /data/local/tmp/edgegest/*.sh; chmod 755 /data/local/tmp/edgegest/*.sh"
    Write-Host "Pushed to /data/local/tmp/edgegest"
}
