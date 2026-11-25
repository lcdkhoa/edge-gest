# GEMINI.md

See **[AGENTS.md](./AGENTS.md)** for the canonical, up‑to‑date instructions for this repository
(architecture, build constraints, ADB runbook, testing, and open work). This file exists only so the
Gemini CLI picks up that guidance.

Quick reminders (full detail in AGENTS.md):
- Two processes over TCP `127.0.0.1:47820`: overlay **APK** (`app/`, consumes edge touches) + **helper**
  injector (`src/`, uid shell, injects keys). Never merge them — `WindowManagerService` blocks overlays
  from `app_process` (`Unknown pid uid=2000`).
- Java: **no lambdas / anonymous / inner classes** (d8 34 crashes); static nested classes only;
  `-source 8`.
- `adb install` is blocked by MIUI — use `.\run.ps1 push-apk` and tap‑install; never spoof the installer.
- Respond to the user in **Vietnamese** with correct diacritics.
