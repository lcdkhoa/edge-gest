# AGENTS.md

Canonical instructions for any coding agent (Codex, Cursor, Gemini, Claude Code, …) working in this
repository. Claude Code also reads `CLAUDE.md`; Cursor reads `.cursor/rules/`; Gemini reads `GEMINI.md`
— those point here. Keep this file as the source of truth and mirror material changes into `CLAUDE.md`.

EdgeGest adds edge‑swipe navigation (Back / Home / Recents / switch app) to a Xiaomi/HyperOS phone that
runs a third‑party launcher (Lawnchair), **without root and without Accessibility** — the two things
HyperOS and the user's bank/government apps respectively refuse. `README.md` holds the full runbook; this
file is what is non‑obvious when changing the code.

Communicate with this user in **Vietnamese** (Tiếng Việt), with correct diacritics. Keep code identifiers
and technical terms in their original form.

**Changelog rule:** After **every** change to code, scripts, manifest, or docs, append a new entry
`{ id, date, decision, notes, tags }` (incrementing `D-XX`) to `DECISIONS[]` in `changelog.html`
(set `reverses: true` when superseding an earlier decision).

## Two‑component architecture (the key mental model)

Navigation is split across two processes that talk over **TCP `127.0.0.1:47820`**:

1. **Overlay APK** — `app/`, package `com.lcd.edgegest`, runs as a normal app uid. Draws thin
   `TYPE_APPLICATION_OVERLAY` strips at the screen edges. An overlay window receives `ACTION_DOWN`, so it
   **consumes** the touch and the app underneath never sees it — this is the whole point: it removes the
   gesture/app double‑trigger. `StripView` classifies the swipe; `GestureClient` sends `'D'` on
   `ACTION_DOWN` and one command char (`B`/`H`/`R`/`S`) to the helper. `OverlayService` keeps the strips
   alive and rebuilds them on rotation. `ShizukuProvider` (`com.lcd.edgegest.shizuku`) receives the Shizuku Binder via `sendBinder`, and
   `ShizukuStarter` talks to `moe.shizuku.privileged.api` over Binder to launch the bundled helper
   (`com.lcd.edgegest.Main --server --hybrid` directly from `sourceDir` `base.apk`) from `BootReceiver`/`MainActivity`.
2. **Helper injector** — `src/`, runs via `app_process` as uid `shell` (2000). In `--server --hybrid` mode
   (`device/start-server.sh`) it listens on the TCP port, injects keys (`KEYCODE_BACK` / `HOME` /
   `APP_SWITCH`) via `InputManager` (fallback: `input`), keeps `force_fsg_nav_bar=1`, and keeps
   `ReaderLoop` (`/dev/input/eventX`) running as an automatic fallback only when the overlay did not
   receive `ACTION_DOWN` (`'D'` — e.g. when a banking app hides overlays via
   `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`).

**Why the split:** a normal app cannot inject global Back/Recents without Accessibility or the
signature‑level `INJECT_EVENTS`. The helper has `INJECT_EVENTS` (uid shell) but **cannot create the
overlay itself** — `WindowManagerService` rejects windows from `app_process` with `Unknown pid uid=2000`
(the process is not registered with ActivityTaskManager). So touch‑consuming must live in the APK and key
injection in the helper.

**Fallback mode** — `src/Main.java` with no `--server`: the helper reads `/dev/input/event7` directly and
classifies gestures itself (`Gestures.java`). Works even inside apps that hide overlays, but **cannot
consume the touch**, so swipes leak through to the app below.

## Build & deploy

Needs Android SDK **build-tools 34.0.0** + **platform android-35** and a JDK. The SDK path is hardcoded as
`$env:LOCALAPPDATA\Android\Sdk` in the PowerShell build scripts (Windows host).

```powershell
.\build-apk.ps1          #             -> app\out\edgegest.apk (bundles both app/src UI & src helper)
.\build.ps1 -Push        # optional    -> helper dex only to /data/local/tmp/edgegest
```

**Hard constraint — no lambdas, anonymous classes, method references, non‑static inner classes, or generic
interface implementations emitting synthetic bridge methods in any Java file.** `d8` 34.0.0 crashes
(`NullPointerException ... String.length()`) on synthetic classes or bridge methods emitted by a modern
JDK (e.g., use raw `Parcelable.Creator` instead of `Parcelable.Creator<T>`), and `android.jar` lacks
`LambdaMetafactory` so `-source 8` lambdas fail to compile. Use only **static nested classes with explicit
constructors** (see `StripView.HoldCheck`, `Main.CommandServer`) and package-private access where nested
classes touch enclosing members. Compilation is `-source 8 -target 8`.

**MIUI blocks `adb install`** (`INSTALL_FAILED_USER_RESTRICTED`) even with "Install via USB" enabled. Do
**not** work around it by spoofing the installer id (`-i com.android.vending`) — that was explicitly
rejected. Push the APK and have the user tap‑install: `.\run.ps1 push-apk`. After install, grant overlay
with `.\run.ps1 grant` (`appops set com.lcd.edgegest SYSTEM_ALERT_WINDOW allow`) — it is an app‑op, not a
runtime permission.

## Driving it over ADB

`run.ps1` is the control surface and avoids reinstalling for most tuning. Typical order:

```powershell
.\run.ps1 push-helper
.\run.ps1 helper-server          # start the injector (overlay is useless without it)
.\run.ps1 grant
.\run.ps1 overlay 40 24 false    # side(dp) bottom(dp) debug  -> live-retune, no reinstall
.\run.ps1 status ; .\run.ps1 log
```

`OverlayService.onStartCommand` reads `side`/`bottom`/`topGuard`/`bottomGap`/`debug` from **Intent extras**
(`am start-foreground-service ... --ei side N --ei bottom N --ez debug true|false --ei topGuard P --ei bottomGap G`),
and persists any supplied extras back to `SharedPreferences` (`edgegest`) so `BootReceiver` keeps the
tuned values across reboots (defaults: `40`/`24`/`11%`/`0dp`). Don't `am force-stop` then immediately
restart in one session: `START_STICKY` races a null‑intent restart against your command (guarded via the
`started` flag, but avoid it).

**Emergency recovery** if navigation gets stuck (no nav bar and no working gesture): `.\run.ps1 fsg 0`
restores the 3‑button bar. The helper's exit wrapper also resets `force_fsg_nav_bar` to 0.

## Testing

SELinux lets uid shell **read** `/dev/input/event7` but **not write** it (`sendevent` → Permission denied),
so touches can't be simulated — verify gestures by hand on the device while tailing
`/data/local/tmp/edgegest/edgegest.log` (helper logs `cmd BACK` etc.; the APK logs under logcat tag
`EdgeGest`). `device/sim.sh` exists but only works where writing the touch node is permitted.

## Remaining on-device verification (see README "Trạng thái & TODO")

All planned code changes (`MainActivity` theme/auto-start, `SharedPreferences` persistence, `BootReceiver`
Direct Boot guard, `topGuard`/`bottomGap` + `HoldCheck`, `--hybrid` `/dev/input` fallback, and
`ShizukuProvider`/`ShizukuStarter`) are implemented, built cleanly, and verified live on device (`c92b13cf`).
Only two manual checks remain:
1. Verify reboot auto-start end-to-end (requires Shizuku's "Start on boot" / Wireless debugging enabled).
2. Open a bank app to verify `--hybrid` fallback and fine-tune `topGuard`/`bottomGap` if needed.
