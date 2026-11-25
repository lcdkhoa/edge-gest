# Tiện ích điều khiển EdgeGest qua ADB (Windows PowerShell).
# Dùng:  .\run.ps1 <lệnh> [tham số]
#
#   push-helper              Build dex helper và đẩy lên /data/local/tmp/edgegest
#   helper-server            Chạy helper ở chế độ server (dùng với APK overlay)
#   helper-reader            Chạy helper ở chế độ đọc /dev/input (không overlay) [--edge=, --debug]
#   helper-stop              Dừng helper, trả lại thanh 3 nút
#   build-apk                Build APK overlay
#   push-apk                 Đẩy APK vào /sdcard/Download và mở trình cài (MIUI chặn adb install)
#   grant                    Cấp quyền overlay (SYSTEM_ALERT_WINDOW) cho APK
#   overlay <side> <bottom> [debug] [topGuard%] [bottomGap]
#                                    Bật/chỉnh overlay, vd: .\run.ps1 overlay 40 24 false 11 0
#   overlay-stop             Tắt overlay
#   log                      Xem log helper
#   fsg <0|1>                Ẩn (1) / hiện (0) thanh 3 nút
#   status                   Trạng thái tiến trình & cửa sổ
param(
    [Parameter(Position = 0)][string]$cmd,
    [Parameter(Position = 1)][string]$a1,
    [Parameter(Position = 2)][string]$a2,
    [Parameter(Position = 3)][string]$a3,
    [Parameter(Position = 4)][string]$a4,
    [Parameter(Position = 5)][string]$a5
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$dir = "/data/local/tmp/edgegest"
$pkg = "com.lcd.edgegest"

switch ($cmd) {
    "push-helper"  { & "$root\build.ps1" -Push }
    "helper-server" {
        adb shell mkdir -p $dir
        adb push "$root\device\start-server.sh" "$dir/start-server.sh" | Out-Null
        adb shell "sed -i 's/\r`$//' $dir/start-server.sh; chmod 755 $dir/start-server.sh"
        adb shell sh "$dir/start-server.sh" $a1
    }
    "helper-reader" {
        adb push "$root\device\start.sh" "$dir/start.sh" | Out-Null
        adb shell "sed -i 's/\r`$//' $dir/start.sh; chmod 755 $dir/start.sh"
        adb shell sh "$dir/start.sh" $a1 $a2
    }
    "helper-stop"  { adb shell sh "$dir/stop.sh" }
    "build-apk"    { & "$root\build-apk.ps1" }
    "push-apk" {
        adb push "$root\app\out\edgegest.apk" /sdcard/Download/edgegest.apk | Out-Null
        adb shell am start -a android.intent.action.VIEW -d "file:///sdcard/Download/edgegest.apk" -t "application/vnd.android.package-archive"
        Write-Host "Mở trình cài trên điện thoại -> bấm Install/Update"
    }
    "grant" {
        adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow
        adb shell pm grant $pkg moe.shizuku.manager.permission.API_V23
        adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS
        adb shell pm grant $pkg android.permission.ACCESS_LOCAL_NETWORK 2>$null
        adb shell cmd appops get $pkg SYSTEM_ALERT_WINDOW
    }
    "overlay" {
        $side = if ($a1) { $a1 } else { 40 }
        $bottom = if ($a2) { $a2 } else { 24 }
        $debug = if ($a3) { $a3 } else { "false" }
        $extraArgs = @("--ei", "side", $side, "--ei", "bottom", $bottom, "--ez", "debug", $debug)
        if ($a4) { $extraArgs += @("--ei", "topGuard", $a4) }
        if ($a5) { $extraArgs += @("--ei", "bottomGap", $a5) }
        adb shell am start-foreground-service -n "$pkg/.OverlayService" @extraArgs
        Start-Sleep 1
        adb shell "logcat -d -s EdgeGest:* | grep 'strips added' | tail -1"
    }
    "overlay-stop" { adb shell am force-stop $pkg }
    "log"          { adb shell cat "$dir/edgegest.log" }
    "fsg"          { adb shell settings put global force_fsg_nav_bar $a1; adb shell settings get global force_fsg_nav_bar }
    "status" {
        Write-Host "--- helper ---"
        adb shell "ps -A -o PID,ARGS | grep -i -E 'edgegest|Main --' | grep -v grep"
        Write-Host "--- overlay windows ---"
        adb shell "dumpsys window | grep -E 'EdgeGest(Left|Right|Bottom)'"
        Write-Host "--- force_fsg_nav_bar ---"
        adb shell settings get global force_fsg_nav_bar
        Write-Host "--- HOME ---"
        adb shell cmd role get-role-holders android.app.role.HOME
    }
    default { Get-Content "$root\run.ps1" | Select-String -Pattern '^#' | ForEach-Object { $_.Line } }
}
