# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

EdgeGest adds edge-swipe navigation (Back/Home/Recents/switch) to a Xiaomi/HyperOS phone that is
running a third‑party launcher (Lawnchair), **without root and without Accessibility** — the two things
HyperOS and the user's bank/government apps respectively refuse. Read `README.md` for the full runbook;
this file captures what is non‑obvious when changing the code.

> Other agents (Codex, Cursor, Gemini) read `AGENTS.md` (the canonical cross‑agent copy); `GEMINI.md` and
> `.cursor/rules/` point there. Keep `CLAUDE.md` and `AGENTS.md` in sync when changing guidance.

## Two‑component architecture (the key mental model)

Navigation is split across two processes that talk over **TCP `127.0.0.1:47820`**:

1. **Overlay APK** (`app/`, package `com.lcd.edgegest`, runs as a normal app uid). Draws thin
   `TYPE_APPLICATION_OVERLAY` strips at the screen edges. Because an overlay window receives the
   `ACTION_DOWN`, it **consumes** the touch so the app underneath never sees it (this is the whole point —
   it eliminates the gesture/app double‑trigger). `StripView` classifies the swipe and `GestureClient`
   sends a single char (`B`/`H`/`R`/`S`) to the helper.
2. **Helper injector** (`src/`, runs via `app_process` as uid `shell` 2000). In `--server` mode it
   listens on the TCP port and injects the key (`KEYCODE_BACK/HOME/APP_SWITCH`) via `InputManager`
   (falling back to the `input` command). It also keeps `force_fsg_nav_bar=1` to hide the 3‑button bar.

**Why the split:** a normal app cannot inject global Back/Recents without Accessibility or the
signature‑level `INJECT_EVENTS`. The helper has `INJECT_EVENTS` (uid shell) but **cannot create the
overlay itself** — `WindowManagerService` rejects windows from `app_process` with
`Unknown pid uid=2000` (the process isn't registered with ActivityTaskManager). So touch‑consuming must
live in the APK and key injection in the helper.

The helper also has a **fallback mode** (`src/Main.java` default, no `--server`): it reads
`/dev/input/event7` directly and classifies gestures itself (`Gestures.java`). This works even inside
apps that hide overlays, but it **cannot consume the touch**, so swipes leak through to the app.

## Build & deploy

Requires Android SDK **build-tools 34.0.0** + **platform android-35**, and a JDK. SDK path is hardcoded
as `$env:LOCALAPPDATA\Android\Sdk` in the build scripts.

```powershell
.\build.ps1 -Push        # helper dex -> /data/local/tmp/edgegest
.\build-apk.ps1          # -> app\out\edgegest.apk
```

**Hard constraint — no lambdas/anonymous/inner classes anywhere in Java.** `d8` 34.0.0 crashes
(`NullPointerException ... String.length()`) on synthetic classes emitted by a modern JDK, and
`android.jar` lacks `LambdaMetafactory` so `-source 8` lambdas fail to compile. Use only **static nested
classes with explicit constructors** (see `StripView.HoldCheck`, `Main.CommandServer`). Compilation is
`-source 8 -target 8`.

**Installing the APK is blocked by MIUI** (`INSTALL_FAILED_USER_RESTRICTED`) even with "Install via USB"
on. Do **not** spoof the installer id. Instead push to the device and have the user tap‑install:
`.\run.ps1 push-apk`. After install, grant overlay with `.\run.ps1 grant`
(`appops set com.lcd.edgegest SYSTEM_ALERT_WINDOW allow`) — it is not a runtime permission.

## Driving it over ADB

`run.ps1` is the control surface; it avoids re‑installing for most tuning. Typical order:

```powershell
.\run.ps1 push-helper
.\run.ps1 helper-server          # start the injector (must run before overlay is useful)
.\run.ps1 grant
.\run.ps1 overlay 40 24 false    # side(dp) bottom(dp) debug  — live-retunes with no reinstall
.\run.ps1 status ; .\run.ps1 log
```

`OverlayService.onStartCommand` reads `side`/`bottom`/`debug` from **Intent extras** (via
`am start-foreground-service ... --ei side N --ei bottom N --ez debug true|false`), so strip width and the
debug coloring are tuned live without a rebuild. These extras are **not persisted** — a reboot falls back
to `DEF_SIDE`/`DEF_BOTTOM` (see TODO in README). Do not `am force-stop` then immediately restart during a
session: `START_STICKY` races a null‑intent restart against your command (guarded now via the `started`
flag, but avoid it).

**Emergency recovery** if navigation gets stuck (no nav bar, no gesture):
`.\run.ps1 fsg 0` (restores the 3‑button bar). The helper's exit wrapper also resets it.

## Testing notes

SELinux lets uid shell **read** `/dev/input/event7` but **not write** it (`sendevent` → Permission denied),
so touches cannot be simulated — gestures are verified by hand on the device while tailing
`/data/local/tmp/edgegest/edgegest.log` (helper logs `cmd BACK` etc; the APK logs under logcat tag
`EdgeGest`). `device/sim.sh` exists but only works on devices where writing the touch node is permitted.
