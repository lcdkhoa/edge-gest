# Build edgegest.apk (overlay app) và (tuỳ chọn) cài lên máy.
#   .\build-apk.ps1            -> build APK
#   .\build-apk.ps1 -Install   -> build + adb install -r
param([switch]$Install)
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$app = "$root\app"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt = "$sdk\build-tools\34.0.0"
$androidJar = "$sdk\platforms\android-35\android.jar"
$out = "$app\out"

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes" | Out-Null

# 1) aapt2 link: tạo APK nền (manifest + bảng tài nguyên)
& "$bt\aapt2.exe" link `
    -I $androidJar `
    --manifest "$app\AndroidManifest.xml" `
    --min-sdk-version 30 --target-sdk-version 33 `
    --version-code 1 --version-name "1.0" `
    -o "$out\base.apk"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# 2) javac (gồm cả UI/Overlay app/src và Helper src để cài 1 APK duy nhất là đủ)
$appSrcs = Get-ChildItem -Recurse "$app\src" -Filter *.java | ForEach-Object { $_.FullName }
$helperSrcs = Get-ChildItem -Recurse "$root\src" -Filter *.java | ForEach-Object { $_.FullName }
$srcs = @($appSrcs) + @($helperSrcs)
& javac -source 8 -target 8 -bootclasspath $androidJar -Xlint:-options -encoding UTF-8 -d "$out\classes" @srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 3) d8 -> classes.dex
$classes = Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --release --min-api 30 --lib $androidJar --output $out @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 4) nhét classes.dex vào base.apk
python -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'classes.dex'); z.close()" "$out\base.apk" "$out\classes.dex"
if ($LASTEXITCODE -ne 0) { throw "add dex failed" }

# 5) zipalign
& "$bt\zipalign.exe" -f -p 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 6) ký bằng debug keystore
$ks = "$env:USERPROFILE\.android\debug.keystore"
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias androiddebugkey --out "$out\edgegest.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "Built: $out\edgegest.apk"

if ($Install) {
    adb install -r -g "$out\edgegest.apk"
}
