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

1. **`--server` (dùng với APK overlay — KIẾN TRÚC CHÍNH):** helper chỉ nhận lệnh TCP và inject.
   APK lo phần nhận diện + nuốt chạm. Ưu điểm: không trùng chạm. Nhược: trong app ngân hàng bật
   `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` thì dải overlay bị ẩn (mất gesture trong app đó — cần kiểm chứng).

2. **Đọc `/dev/input` (không overlay — bản đầu, giữ làm fallback):** helper tự đọc `/dev/input/event7`,
   nhận diện cử chỉ, inject. Chạy mọi nơi kể cả app ngân hàng, NHƯNG không nuốt được chạm (cú vuốt
   vẫn lọt xuống app). Logic phân loại ở `Gestures.java`.

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

- **APK**: có `BootReceiver` (BOOT_COMPLETED) + foreground service. Cần bật **Autostart** cho EdgeGest
  trong Security/Permissions của HyperOS.
- **Helper**: tiến trình ADB chết khi reboot → cần **Shizuku** (kích hoạt bằng Wireless debugging, không root)
  chạy `sh /data/local/tmp/edgegest/start-server.sh` lúc boot. (CHƯA dựng — xem TODO.)

---

## Cấu trúc thư mục

```
src/com/lcd/edgegest/        Helper dex (chạy bằng app_process, uid shell)
  Main.java                   entry; --server (TCP injector) | mặc định (đọc /dev/input)
  Env.java                    systemContext, inject phím (InputManager + fallback `input`), switch app
  Gestures.java               phân loại cử chỉ cho chế độ /dev/input (thuần Java)
  ShellContext.java           Context giả package com.android.shell
app/                         APK overlay
  AndroidManifest.xml
  src/com/lcd/edgegest/
    OverlayService.java        foreground service, dựng dải, xử lý xoay màn hình
    StripView.java             dải cảm ứng, nhận diện cử chỉ, gửi lệnh TCP
    GestureClient.java         client TCP tới helper
    MainActivity.java          màn hình cấu hình (ĐANG LỖI: hiển thị trắng — xem TODO)
    BootReceiver.java          tự chạy khi boot
device/                      start.sh (reader), start-server.sh (server), stop.sh, sim.sh
build.ps1 / build-apk.ps1    build helper dex / APK
run.ps1                      tiện ích điều khiển qua ADB
```

---

## Trạng thái & TODO

**Đã chạy được (đã kiểm chứng trên máy):**
- Overlay nuốt chạm, hết trùng với app.
- Back / Home / Recents (vuốt-giữ) / đổi app (vuốt ngang) — OK.
- Xoay màn hình: dải dựng lại theo cả dọc/ngang (kể cả game ngang).
- Ẩn thanh 3 nút qua `force_fsg_nav_bar=1`; helper tự khôi phục khi thoát.

**Còn làm:**
1. **MainActivity hiển thị trắng** — có thể do thiếu `android:theme`. Cần thêm theme DeviceDefault và/hoặc
   tự bật service khi mở app. Hiện đang điều khiển hoàn toàn qua `run.ps1`/adb.
2. **Lưu độ dày để dùng sau reboot** — hiện truyền qua Intent extras (không lưu). Nên lưu vào
   SharedPreferences trong `OverlayService.onStartCommand` khi có extras, để BootReceiver đọc lại.
   (Hoặc đổi hằng `DEF_SIDE`/`DEF_BOTTOM` = 40/24.)
3. **Shizuku tự chạy helper khi boot** — dựng app/macro gọi `start-server.sh` qua Shizuku.
4. **Kiểm chứng app ngân hàng** có ẩn overlay (`HIDE_NON_SYSTEM_OVERLAY_WINDOWS`) không. Nếu có,
   cân nhắc chạy SONG SONG chế độ `/dev/input` làm fallback khi overlay bị ẩn.
5. Cân nhắc kẹp giữ cạnh để tránh chạm nhầm bàn phím/nút sát mép (tinh chỉnh `topGuard`/độ dày).

## Ghi chú kỹ thuật (các ngõ đã thử & loại)
- Overlay từ chính helper `app_process`: `WindowManagerService` chặn "Unknown pid uid=2000"
  (process không được ATM đăng ký) → phải tách overlay ra APK cài đặt.
- Sửa thẳng `com.miui.home`: gating ở native `libapp_launcher.so` (Rust/Flutter) so sánh cứng
  package `com.miui.home`; APK ký chữ ký platform → không cài đè khi no-root.
- SELinux cho shell ĐỌC `/dev/input/event7` nhưng không GHI (`sendevent` Permission denied) → không
  tự giả lập chạm để test được; test bằng tay.
