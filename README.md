# EdgeGest — gesture điều hướng cho Xiaomi/HyperOS khi dùng launcher bên thứ ba

Giải quyết: HyperOS chặn full-screen gesture khi launcher mặc định không phải MIUI Launcher
(máy dùng Lawnchair `app.lawnchair.play`), **không root**, và các app ngân hàng/chính phủ **chặn Accessibility**.

Thiết bị phát triển: Xiaomi `25113PN0EG`, HyperOS 4.0.3 (`OS4.0.3.0.XPCMIXM`), Android 17.

---

## Kiến trúc

Có hai thành phần, nối với nhau qua TCP localhost:

```
┌─────────────────────────┐        127.0.0.1:47820         ┌──────────────────────────┐
│  APK overlay (uid app)  │  ──── 'B'/'H'/'R'/'S' ───────▶ │  Helper injector (uid 2000)│
│  - dải mép TYPE_APP_     │                                │  - app_process qua ADB/    │
│    OVERLAY, NUỐT chạm    │                                │    Shizuku                 │
│  - nhận diện cử chỉ      │                                │  - INJECT_EVENTS: phát     │
└─────────────────────────┘                                │    Back/Home/Recents       │
                                                            │  - giữ force_fsg_nav_bar=1 │
                                                            └──────────────────────────┘
```

**Vì sao tách hai phần:** một app thường KHÔNG inject được phím Back/Recents (cần `INJECT_EVENTS`,
là quyền signature) — nếu không có Accessibility. Nên:
- **APK** (process được hệ thống đăng ký) tạo được overlay window để **nuốt** cú chạm → hết trùng với app.
- **Helper** chạy uid `shell` (2000) có `INJECT_EVENTS` → phát phím. Không dùng Accessibility ở đâu cả.

### Cử chỉ
| Cử chỉ | Lệnh | Hành động |
|---|---|---|
| Vuốt từ mép trái/phải vào trong | `B` | Back |
| Vuốt lên từ mép dưới (nhanh) | `H` | Home |
| Vuốt lên từ mép dưới rồi giữ (≥300ms) | `R` | Recents |
| Vuốt ngang ở mép dưới | `S` | App dùng gần nhất |

---

## Hai chế độ của helper

1. **`--server --hybrid` (mặc định trong `start-server.sh` — KIẾN TRÚC CHÍNH):** helper nhận lệnh TCP từ APK overlay để inject, đồng thời giữ `ReaderLoop` (`/dev/input/eventX`) ở trạng thái dự phòng. Khi overlay nhận chạm bình thường, `StripView` báo `'D'` (`ACTION_DOWN`) nên reader bỏ qua (không trùng chạm, không lọt xuống app). Khi vào app ngân hàng bật `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` làm overlay bị ẩn, reader tự động phát hiện không có `'D'` từ overlay và inject fallback.
2. **Đọc `/dev/input` thuần (không overlay — giữ qua `start.sh`):** helper tự đọc `/dev/input/event7`, nhận diện cử chỉ, inject. Chạy mọi nơi nhưng không nuốt được chạm (cú vuốt vẫn lọt xuống app). Logic phân loại ở `Gestures.java`.

---

## Build

Yêu cầu: Android SDK (build-tools 34.0.0, platform android-35), JDK (đã test JDK 21).
> Lưu ý d8 34.0.0 + JDK mới hay crash với lambda/anonymous/inner class →
> **chỉ dùng static nested class, không lambda/method-ref** trong code (xem các file hiện có).

```powershell
.\build.ps1 -Push        # build helper dex -> /data/local/tmp/edgegest
.\build-apk.ps1          # build app\out\edgegest.apk
```

### Cài APK (MIUI chặn `adb install`)
`adb install` trả `INSTALL_FAILED_USER_RESTRICTED` dù đã bật "Install via USB" (MIUI còn đòi xác minh
tài khoản Mi). Cách dùng: đẩy APK vào máy rồi bấm cài thủ công:
```powershell
.\run.ps1 push-apk       # -> /sdcard/Download + mở trình cài; bấm Install/Update trên máy
```

---

## Runbook (dùng `run.ps1`)

```powershell
.\run.ps1 push-helper                 # build + đẩy helper dex
.\run.ps1 helper-server               # chạy helper chế độ server (cho overlay)
.\run.ps1 build-apk
.\run.ps1 push-apk                    # cài/cập nhật APK (bấm trên máy)
.\run.ps1 grant                       # cấp quyền overlay
.\run.ps1 overlay 40 24 true          # bật overlay: side=40dp bottom=24dp debug(hiện màu)=true
.\run.ps1 overlay 40 24 false         # tắt màu debug (dùng hằng ngày) — chỉnh số để đổi độ dày (live, không cài lại)
.\run.ps1 overlay 40 24 false 11 0    # thêm tuỳ chọn topGuard(%) và bottomGap(dp) — tự lưu SharedPreferences
.\run.ps1 overlay-stop                # tắt overlay
.\run.ps1 helper-stop                 # dừng helper, trả lại thanh 3 nút
.\run.ps1 status                      # xem trạng thái
.\run.ps1 log                         # xem log helper
.\run.ps1 fsg 0                        # hiện lại thanh 3 nút khẩn cấp
```

Thứ tự khởi động đầy đủ: `push-helper` → `helper-server` → (`build-apk` → `push-apk` nếu đổi code APK)
→ `grant` → `overlay 40 24 false`.

Khẩn cấp nếu kẹt điều hướng: `.\run.ps1 fsg 0` (hoặc `adb shell settings put global force_fsg_nav_bar 0`).

---

## Tự chạy sau khi khởi động lại

- **APK**: `BootReceiver` (`BOOT_COMPLETED`, kiểm tra `UserManager.isUserUnlocked()` trước khi đọc CE `SharedPreferences`) tự bật `OverlayService` với cấu hình đã lưu. Cần bật **Autostart** cho EdgeGest trong Security/Permissions của HyperOS.
- **Helper**: tiến trình ADB chết khi reboot. `BootReceiver` (và `MainActivity`) đã tích hợp `ShizukuStarter` gọi `sh /data/local/tmp/edgegest/start-server.sh` qua **Shizuku** (`moe.shizuku.privileged.api`) ngay sau khi mở khoá. Chỉ cần Shizuku được kích hoạt (Wireless debugging) và đã cấp quyền cho EdgeGest.

---

## Cấu trúc thư mục

```
src/com/lcd/edgegest/        Helper dex (chạy bằng app_process, uid shell)
  Main.java                   entry; --server [--hybrid] (TCP + fallback) | mặc định (đọc /dev/input)
  Env.java                    systemContext, inject phím (InputManager + fallback `input`), switch app
  Gestures.java               phân loại cử chỉ cho chế độ /dev/input (thuần Java)
  ShellContext.java           Context giả package com.android.shell
app/                         APK overlay
  AndroidManifest.xml
  src/com/lcd/edgegest/
    OverlayService.java        foreground service, dựng dải, lưu SharedPreferences, xử lý xoay màn hình
    StripView.java             dải cảm ứng, nhận diện cử chỉ, haptic HoldCheck, gửi 'D'/'B'/'H'/'R'/'S'
    GestureClient.java         client TCP tới helper
    MainActivity.java          màn hình cấu hình, trạng thái live, nút Shizuku/overlay
    BootReceiver.java          tự bật OverlayService + gọi ShizukuStarter khi boot (sau unlock)
    ShizukuStarter.java        giao tiếp Binder Shizuku v13+ để chạy start-server.sh
  src/moe/shizuku/api/
    BinderContainer.java       Parcelable tương thích Shizuku Provider (raw Creator tránh crash d8)
device/                      start.sh (reader), start-server.sh (server --hybrid), stop.sh, sim.sh
build.ps1 / build-apk.ps1    build helper dex / APK
run.ps1                      tiện ích điều khiển qua ADB
changelog.html               nhật ký thay đổi & quyết định kỹ thuật
```

---

## Trạng thái & TODO

**Đã hoàn thành trong code & kiểm chứng build:**
- Overlay nuốt chạm, hết trùng với app; Back / Home / Recents (kèm haptic mốc 300ms `HoldCheck`) / đổi app; xoay màn hình; ẩn thanh 3 nút (`force_fsg_nav_bar=1`).
- `MainActivity` đã có `Theme.DeviceDefault.DayNight`, xử lý insets, trạng thái live (overlay/service/helper/Shizuku), tự bật service & helper khi mở app.
- Lưu `SharedPreferences` cả khi chỉnh qua Intent extras (`side`, `bottom`, `topGuard`, `bottomGap`, `debug`) với mặc định `40`/`24`/`11%`/`0dp`.
- `BootReceiver` an toàn Direct Boot (`isUserUnlocked()`) và tích hợp `ShizukuStarter` tự khởi chạy helper lúc boot.
- Chế độ `--server --hybrid` chạy song song `/dev/input` fallback, tự triệt tiêu khi overlay gửi `'D'` (`ACTION_DOWN`) và fallback khi app ngân hàng ẩn overlay.

**Còn kiểm chứng thực tế trên máy sau khi cài bản APK mới (`.\run.ps1 push-apk`):**
1. Kiểm tra giao diện `MainActivity` mới trên HyperOS, cấp quyền Shizuku trong app và thử reboot máy để xác nhận `BootReceiver` + Shizuku tự bật lại đầy đủ.
2. Mở app ngân hàng (vd. VCB / BVBank / MoMo) để xác nhận chế độ `--hybrid` tự fallback mượt mà nếu overlay bị ẩn, và tinh chỉnh `topGuard`/`bottomGap` theo thói quen gõ phím.

## Ghi chú kỹ thuật (các ngõ đã thử & loại)
- Overlay từ chính helper `app_process`: `WindowManagerService` chặn "Unknown pid uid=2000"
  (process không được ATM đăng ký) → phải tách overlay ra APK cài đặt.
- Sửa thẳng `com.miui.home`: gating ở native `libapp_launcher.so` (Rust/Flutter) so sánh cứng
  package `com.miui.home`; APK ký chữ ký platform → không cài đè khi no-root.
- SELinux cho shell ĐỌC `/dev/input/event7` nhưng không GHI (`sendevent` Permission denied) → không
  tự giả lập chạm để test được; test bằng tay.
