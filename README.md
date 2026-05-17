<div align="center">

# ⚡ MobileFTP

**Light-Speed File Transfer for Android**

A production-ready FTP **server _and_ client** in your pocket — built with
Kotlin, Jetpack Compose, and a chunked parallel transfer engine that pushes
your network to the limit.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/adityabhalsod/mobile-ftp/release.yml?branch=main&label=Release%20CI)](https://github.com/adityabhalsod/mobile-ftp/actions)

[Download APK](https://github.com/adityabhalsod/mobile-ftp/releases/latest) ·
[Report Bug](https://github.com/adityabhalsod/mobile-ftp/issues/new) ·
[Request Feature](https://github.com/adityabhalsod/mobile-ftp/issues/new)

</div>

---

## 📖 Table of Contents

- [What is MobileFTP?](#-what-is-mobileftp)
- [Highlights](#-highlights)
- [Three-Tab Dashboard](#-three-tab-dashboard)
- [Architecture](#-architecture)
- [Performance Engine — 10 Laws](#-performance-engine--10-laws)
- [Tech Stack](#-tech-stack)
- [Permissions](#-permissions)
- [Requirements](#-requirements)
- [Getting Started](#-getting-started)
- [Build & Run](#-build--run)
- [Wireless ADB Setup](#-wireless-adb-setup)
- [CI / CD Pipeline](#-cicd-pipeline)
- [Project Structure](#-project-structure)
- [Configuration Reference](#%EF%B8%8F-configuration-reference)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

---

## 👋 What is MobileFTP?

MobileFTP turns your Android phone into a **two-way file portal**:

- **Run an FTP server** — phone becomes reachable from any FTP client
  (Windows Explorer, FileZilla, Cyberduck, your Mac's Finder). Tap Start
  and your photos, downloads, and documents are instantly browsable over the LAN.
- **Connect as an FTP client** — paste a host, get a fast file browser with
  multi-select downloads, parallel transfers, and resumable jobs.

Designed around three principles:

1. **Speed first.** Every file >1 MB is split into N parallel chunks (default 8),
   each on its own FTP data connection. Throughput scales with network capacity,
   not protocol overhead.
2. **Surgical UI.** Raycast-inspired Obsidian-dark surfaces, hairline borders,
   command-palette density. Auto light/dark mode with a manual override.
3. **Resilient transfers.** Per-chunk MD5 verification, 2-second checkpoints
   to Room, automatic resume from the last verified offset on failure.

---


## ✨ Highlights

| Capability | Detail |
|---|---|
| 🔁 **Bi-directional** | Server _and_ client modes in a single app |
| ⚡ **Parallel chunks** | Files split into N=2–32 streams, transferred concurrently |
| 📊 **Live throughput** | 250 ms sampling, 2 s sliding window, gradient area chart |
| 💾 **Resumable** | Per-chunk MD5 + 2 s checkpoints, restart only failed chunks |
| 🔌 **Connection pool** | Pre-warmed FTP clients, no teardown between files |
| 🧠 **Adaptive buffers** | 200 ms bandwidth probe + live recalc every 5 s |
| 📦 **LZ4 compression** | Probe first 64 KB; skip already-compressed bytes |
| 📡 **Network failover** | Scores WiFi Direct / 5 GHz / 2.4 GHz / Ethernet / hotspot |
| 🔒 **Secure creds** | Passwords encrypted via Android Keystore + EncryptedSharedPreferences |
| 🔐 **FTPS-ready** | TLS 1.2+ control & data channels (toggle in config) |
| 🌙 **Auto theme** | System dark/light detection + manual override |
| 📱 **QR pairing** | Tap to copy `ftp://user:pass@host:port` connection URL |
| 🔋 **Foreground service** | Persistent notification while server runs |
| 🚀 **Background transfers** | WorkManager with progress notifications |

---

## 📱 Three-Tab Dashboard

### Tab 1 · Server

> Spin up an FTP server with one tap.

- **Status hero card** — running/stopped pill, LAN + public IP, bound port,
  active root path, network interface badges.
- **All Files Access banner** — surfaces missing `MANAGE_EXTERNAL_STORAGE` with
  a one-tap shortcut to system Settings.
- **QR code** — tap to copy the full connection URL with haptic feedback.
- **Connections card** — live count of connected clients with IP and connect time.
- **Throughput graph** — 60-sample area chart with current / peak / average chips.
- **Configuration** — port, credentials, PASV range, FTPS toggle, anonymous toggle,
  shared directory picker (SAF), max connections slider, chunk count slider.

### Tab 2 · Transfers

> Watch every byte move.

- **Active / Pending / Completed** sections with section counts.
- **Per-job cards** that expand to per-chunk detail (offset, transferred, speed, state).
- **Action buttons** per row: cancel, retry, remove.
- **Empty state** with a flame icon when nothing's queued.

### Tab 3 · Client

> Browse remote FTP servers like a local file manager.

- **Saved profiles** — name, host, port, last connected timestamp.
- **Connection sheet** — modal with host, port, credentials, PASV/FTPS toggles,
  chunk count override.
- **File browser** — sticky breadcrumbs, sortable name/size/date, multi-select,
  long-press context menu (download, rename, delete), `+` button for new folder.

---

## 🏗️ Architecture

Strict **MVVM + Repository** with one-way data flow:

```
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                       │
│   ServerScreen · ClientScreen · TransferQueueScreen         │
└──────────────┬──────────────────────────────────────────────┘
               │ collectAsState()
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  ViewModels (Hilt)                          │
│   ServerVM · ClientVM · FileBrowserVM · TransferQueueVM     │
└──────────────┬──────────────────────────────────────────────┘
               │ suspend fun / Flow<>
               ▼
┌─────────────────────────────────────────────────────────────┐
│                 UseCases (domain/usecase/)                  │
│   StartServer · ConnectClient · Download · Upload · Resume  │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│             Repositories (data/repository/)                 │
│   FtpServerRepo · FtpClientRepo · TransferRepo · …          │
└──────┬─────────────────────────────────────────┬────────────┘
       │                                         │
       ▼                                         ▼
┌──────────────────┐                  ┌─────────────────────────┐
│ Network Engines  │                  │  Local Persistence      │
│ ChunkTransfer    │                  │  Room (jobs, chunks,    │
│ ConnectionPool   │                  │       profiles)         │
│ ThroughputMonitor│                  │  DataStore (settings)   │
│ AdaptiveBuffer   │                  │  EncryptedShared (creds)│
│ Lz4Compression   │                  └─────────────────────────┘
│ NetworkInterface │
│ SocketTuner      │
└──────────────────┘
```

---


## 🧪 Performance Engine — 10 Laws

These ten laws govern every byte that moves through MobileFTP. Each is
independently verifiable in code.

| # | Law | Module |
|---|---|---|
| **P1** | Parallel multi-stream chunk engine — files >1 MB split into N (2–32) concurrent streams | `network/ChunkTransferEngine.kt` |
| **P2** | Zero-copy I/O — `FileChannel.transferTo` + memory-mapped buffers ≥64 MB | `ChunkTransferEngine` |
| **P3** | Adaptive buffer sizing — 200 ms probe, recalc every 5 s, clamp 64 KB–4 MB | `network/AdaptiveBufferEngine.kt` |
| **P4** | TCP socket tuning — 4 MB SND/RCV, no Nagle, keep-alive | `network/SocketTuner.kt` |
| **P5** | Work-stealing scheduler — `Dispatchers.IO.limitedParallelism(32)` | `ChunkTransferEngine` |
| **P6** | Adaptive LZ4 compression — skip if first-64 KB ratio ≥ 0.95 | `network/Lz4CompressionEngine.kt` |
| **P7** | Resumable transfers — per-chunk MD5 + 2 s Room checkpoints | `worker/FtpTransferWorker.kt` |
| **P8** | Connection pool — borrow/return/warm with `ArrayDeque + Mutex` | `network/FtpConnectionPool.kt` |
| **P9** | Network interface scoring — WiFi Direct > 5 GHz > Ethernet > 2.4 GHz > Hotspot > Cellular | `network/NetworkInterfaceSelector.kt` |
| **P10** | Real-time throughput monitor — 250 ms sample, 2 s sliding window, ETA | `network/ThroughputMonitor.kt` |

---

## 🛠️ Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 1.9.23 (100% — zero Java sources) |
| UI | Jetpack Compose 1.6.7 + Material 3 1.2.1 |
| DI | Hilt 2.51.1 |
| Async | Coroutines 1.8.0 + Flow |
| Persistence | Room 2.6.1 + DataStore 1.1.1 |
| Background | WorkManager 2.9.0 (foreground transfer worker) |
| Security | EncryptedSharedPreferences (Android Keystore-backed) |
| FTP server | Apache FtpServer 1.2.0 |
| FTP client | Apache Commons Net 3.10.0 |
| Compression | LZ4-Java 1.8.0 |
| HTTP | OkHttp 4.12.0 (public-IP probe only) |
| QR codes | ZXing core 3.5.3 (writer only — no camera scanner) |
| Build | AGP 8.4.0 + Gradle 8.7 (JDK 17) |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |

---

## 🔐 Permissions

Trimmed to the bare minimum the app actually uses:

| Permission | Why | When |
|---|---|---|
| `INTERNET` | FTP transport | always |
| `ACCESS_NETWORK_STATE` | network change detection (P9 failover) | always |
| `ACCESS_WIFI_STATE` | WiFi LAN IP detection | always |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS / network discovery | always |
| `MANAGE_EXTERNAL_STORAGE` | serve real phone files (DCIM, Downloads…) | runtime, user-granted |
| `READ_EXTERNAL_STORAGE` | legacy fallback for Android ≤ 12L | runtime, max SDK 32 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | keep server alive while phone is locked | always (declared) |
| `POST_NOTIFICATIONS` | live status notification on Android 13+ | runtime prompt |

**No** Camera, Location, Nearby Devices, Photos & Videos, Music & Audio,
Contacts, Phone, Microphone, or any other permission you'd typically associate
with a sketchy "free" app. The QR feature uses a writer-only library; nothing
in the app activates the camera.

---

## 🔧 Requirements

### Development environment

| Tool | Required | Recommended |
|---|---|---|
| Android Studio | Hedgehog (2023.1.1)+ | latest stable |
| JDK | 17 | Temurin 17 LTS |
| Gradle | 8.7 (via wrapper) | included in repo |
| Node.js | 14+ (for `setup.js`) | 18 LTS |
| ADB | included in platform-tools | latest |

### Target device

| Spec | Required |
|---|---|
| Android version | 8.0+ (API 26+) |
| Storage | < 20 MB installed |
| Architecture | any (no NDK) |

---

## 🚀 Getting Started

### 1. Clone

```bash
git clone https://github.com/adityabhalsod/mobile-ftp.git
cd mobile-ftp
```

### 2. One-command deploy (USB)

Plug in your phone with USB debugging enabled, then:

```bash
node setup.js deploy
```

That single command runs `init` → `build` → `install` → `launch`. The script:

- auto-detects your Android SDK from `ANDROID_HOME` or default Windows / macOS / Linux paths
- auto-downloads the Gradle 8.7 wrapper jar if missing
- discovers Android Studio's bundled JBR if `JAVA_HOME` isn't set
- picks the first online ADB device (USB or wireless)

### 3. One-command deploy (wireless)

```bash
node setup.js adb-setup    # interactive pairing wizard
node setup.js deploy
```

`adb-setup` walks you through Settings → Developer Options → Wireless Debugging
→ "Pair device with pairing code", saves the IP/port to `device.ini`, and
future `deploy` calls auto-reconnect.

---


## 🔨 Build & Run

### Using `setup.js` (recommended)

```bash
node setup.js                # show all commands
node setup.js init           # write local.properties from ANDROID_HOME
node setup.js build          # gradlew assembleDebug
node setup.js install        # install pre-built APK
node setup.js run            # install + launch
node setup.js deploy         # full pipeline: init + build + install + launch
node setup.js uninstall      # remove app from device
node setup.js logcat         # tail logcat filtered to com.mobileftp
node setup.js adb-setup      # interactive wireless pairing
node setup.js reconnect      # recover an offline wireless ADB session
```

### Using Gradle directly

```bash
# Debug build + install
./gradlew installDebug

# Debug APK only
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (signed if KEYSTORE_FILE env vars are set; unsigned otherwise)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Launch the app
adb shell am start -n com.mobileftp/.MainActivity
```

### Signed release builds

```bash
export KEYSTORE_FILE="/path/to/release.keystore"
export KEYSTORE_PASSWORD="..."
export KEY_ALIAS="release"
export KEY_PASSWORD="..."
./gradlew assembleRelease
```

When these env vars are present, the build wires them into the release
signing config (see `app/build.gradle.kts`).

### Logcat

```bash
# Only MobileFTP logs
adb logcat -s FtpServerService:V FtpServerRepository:V

# All app process logs
adb logcat --pid=$(adb shell pidof com.mobileftp)
```

---

## 📡 Wireless ADB Setup

Skip the cable. From `setup.js`:

```bash
node setup.js adb-setup
```

The wizard asks for:

1. **Pairing IP / port / code** — from "Pair device with pairing code" on the phone
2. **Connect IP / port** — from the main "Wireless Debugging" page (these often
   differ from the pairing port!)

Both pairs are saved to `device.ini` (git-ignored). On every later run,
`setup.js` reconnects automatically and verifies the device state — if it's
gone offline (port rotation, sleep), it kills and restarts the local ADB
server, then reconnects.

If the session goes truly stale, run `node setup.js reconnect` for a
forced recovery without re-pairing.

---

## 🔄 CI / CD Pipeline

The repo ships a GitHub Actions workflow at
[`.github/workflows/release.yml`](.github/workflows/release.yml) that publishes
signed APKs to GitHub Releases on every push to `main`, `beta`, or `alpha`.

### Pipeline steps

```
Push to main / beta / alpha
        │
        ▼
┌─ GitHub Actions ─────────────────────────────────┐
│  1. Checkout (full history for changelog)        │
│  2. Set up JDK 17 (Temurin)                      │
│  3. Set up Android SDK                           │
│  4. Cache Gradle dependencies                    │
│  5. Determine version + build number             │
│  6. Inject versionCode = commits-since-last-tag  │
│  7. Generate categorized changelog from commits  │
│  8. Decode keystore (or auto-generate temp one)  │
│  9. ./gradlew assembleRelease (-x lint -x test)  │
│ 10. Rename APK → mobileftp-vX.Y.Z.apk            │
│ 11. Upload as workflow artifact (30 days)        │
│ 12. Create + push Git tag                        │
│ 13. Create GitHub Release with APK + changelog   │
└──────────────────────────────────────────────────┘
```

### Release channels

| Branch | Channel | Version pattern | Pre-release |
|---|---|---|---|
| `main` | stable | `v1.0.0` | no |
| `beta` | beta | `v1.0.0-beta.N` | yes |
| `alpha` | alpha | `v1.0.0-alpha.N` | yes |

### Auto-generated changelog

Commits are categorized by their conventional-commit prefix:

| Prefix | Section |
|---|---|
| `feat:` | ✨ Features |
| `fix:` | 🐛 Bug Fixes |
| `perf:` | ⚡ Performance |
| `refactor:` | 🧹 Refactor |
| _(other)_ | 📦 Other Changes |

### Required repository secrets

All of these are **optional**. Without them, CI auto-generates a temporary
keystore so every build is at least installable.

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

### Manual release

You can also trigger a release manually from the Actions tab via
`workflow_dispatch`, choosing the `alpha` / `beta` / `main` channel.

---


## 📂 Project Structure

```
mobile-ftp/
├── app/
│   ├── build.gradle.kts                  # AGP + signing config (env-driven)
│   ├── proguard-rules.pro                # R8 keep rules for FtpServer / Commons Net / LZ4
│   └── src/main/
│       ├── AndroidManifest.xml           # 8 permissions, foreground service, WorkManager
│       ├── kotlin/com/mobileftp/
│       │   ├── MainActivity.kt           # entry point, system bar config, permission prompt
│       │   ├── MobileFtpApp.kt           # @HiltAndroidApp + WorkManager.Configuration
│       │   ├── di/                       # Hilt modules (App, Database, Network, Worker)
│       │   ├── data/
│       │   │   ├── local/                # Room DB, DataStore, EncryptedSharedPrefs
│       │   │   └── repository/           # 4 repositories
│       │   ├── domain/
│       │   │   ├── model/                # 7 immutable data classes
│       │   │   └── usecase/              # 7 use cases
│       │   ├── network/                  # 7 perf engines (P1–P10)
│       │   ├── service/                  # FtpServerService (foreground)
│       │   ├── worker/                   # FtpTransferWorker (background transfers)
│       │   ├── ui/
│       │   │   ├── components/           # 11 reusable Compose components
│       │   │   ├── theme/                # Raycast tokens (light + dark)
│       │   │   ├── server/ · client/ · transfers/   # ViewModels + Screens
│       │   │   └── MobileFtpApp.kt       # Bottom-nav scaffold
│       │   └── util/                     # NetworkUtils, StorageUtils, ChecksumUtils, …
│       └── res/                          # icons, themes, strings, font descriptors
├── .github/
│   └── workflows/release.yml             # signed APK release pipeline
├── docs/
│   └── fonts.md                          # optional Inter / JetBrains Mono setup
├── gradle/wrapper/                       # auto-provisioned by setup.js or Studio
├── build.gradle.kts                      # plugins (AGP, Kotlin, Hilt, kapt)
├── settings.gradle.kts                   # repos + module list
├── gradle.properties                     # JVM args, parallel build flags
├── setup.js                              # Node automation (build/install/deploy/logcat)
├── device.ini.example                    # ADB wireless config template
└── README.md                             # this file
```

---

## ⚙️ Configuration Reference

### `device.ini`

Local-only ADB device config (git-ignored). Generated by `node setup.js adb-setup`:

```ini
device.ip=192.168.1.100
device.port=5555
sdk.dir=C:\Users\you\AppData\Local\Android\Sdk
```

### `local.properties`

Generated by `node setup.js init`. Tells Gradle where the Android SDK is.

### Server config (in-app)

Persisted via DataStore (non-sensitive) + EncryptedSharedPreferences (credentials):

| Key | Default | Range / Notes |
|---|---|---|
| Port | 2121 | 1–65535 |
| Username | `mobile` | EncryptedSharedPreferences |
| Password | `ftp` | EncryptedSharedPreferences |
| Shared directory | shared external storage when granted, else app-private | SAF picker |
| PASV port range | 50000–51000 | both inclusive |
| Max connections | 10 | 1–32 |
| Max connections per IP | 4 | informational |
| Anonymous access | off | requires explicit toggle |
| Chunk count (N) | 8 | 2–32 |
| FTPS (TLS) | off | TLS 1.2+ |

---

## 🔍 Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| **"All Files Access required" banner persists after granting** | The resume hook re-checks `Environment.isExternalStorageManager()` on `ON_RESUME`. Pull the screen down and back up if it lingers. |
| **FTP client shows empty folder** | Default root is `/storage/emulated/0/Android/data/com.mobileftp/files/ftp_root` _without_ All Files Access. Grant the permission to expose the real `/storage/emulated/0`. |
| **Server starts then immediately stops** | Port already in use. Check `node setup.js logcat` for `FtpServerRepository: FTP server failed to start: Address already in use`. Change the port in Configuration. |
| **Wireless ADB shows "device offline"** | `node setup.js reconnect` resets the ADB server and re-pairs from `device.ini`. |
| **Gradle build "Couldn't delete R.jar"** | Daemon holding a Windows file lock. Run `gradlew --stop` then build again. |
| **`gradle-wrapper.jar not found`** | `setup.js build` auto-downloads it from the official Gradle GitHub tag. If offline, open the project once in Android Studio. |
| **"Multiple Kotlin daemon sessions" warning** | Run `gradlew --stop` once to clean up daemons from prior failed builds. |

---

## 🤝 Contributing

Pull requests welcome — please follow these guidelines.

### Workflow

1. Fork & clone
2. `git checkout -b feat/your-feature` (or `fix/your-bug`)
3. Make focused commits using
   [Conventional Commits](https://www.conventionalcommits.org/): `feat:`,
   `fix:`, `perf:`, `refactor:`, `docs:`, `chore:`, `test:`
4. `./gradlew assembleDebug` must succeed with **zero warnings**
5. Test on a physical device (some `TrafficStats`-style behaviors don't
   reproduce on emulators)
6. Open a PR against `alpha` for unstable / beta features, `main` for
   stable changes; `beta` is reserved for the maintainer's release prep

### Code style

- Kotlin only — no Java
- Compose only — no XML layouts
- All colors must come from `LocalRaycastColors.current` — **never** hardcode
- Inputs/buttons use the existing `RaycastInput` / `RaycastButton` components
- Repositories return `Result<T>` for fallible operations
- ViewModels expose `StateFlow` only — never `LiveData`

### Ideas for contribution

- 🌍 i18n — translate `strings.xml`
- 🧪 Unit tests for `ChunkTransferEngine` chunk math + resume logic
- 📈 Speed history graph on the Transfers tab
- 🔔 Configurable speed-drop alerts
- 🪟 Tablet-optimized two-pane layout

---

## 📄 License

Released under the [MIT License](LICENSE).

```
MIT License — Copyright (c) 2026 Aditya Bhalsod
```

Built on the shoulders of:

- [Apache FtpServer](https://mina.apache.org/ftpserver-project/) — the embedded server
- [Apache Commons Net](https://commons.apache.org/proper/commons-net/) — the FTP client protocol stack
- [LZ4-Java](https://github.com/lz4/lz4-java) — the fast lossless compressor
- [ZXing](https://github.com/zxing/zxing) — the QR code writer
- [Raycast](https://www.raycast.com/) — design inspiration

---

<div align="center">

⬆ [Back to top](#-mobileftp)

Made with ❤️ by [Aditya](https://github.com/adityabhalsod) ·
Kotlin · Jetpack Compose · MVVM + Hilt

</div>
