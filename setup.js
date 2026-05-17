#!/usr/bin/env node
/**
 * setup.js — build, run, and project setup helper for MobileFTP
 *
 * Usage:
 *   node setup.js adb-setup  → pair + connect wireless ADB, save to device.ini
 *   node setup.js init       → generate local.properties from ANDROID_HOME
 *   node setup.js build      → assemble debug APK via Gradle
 *   node setup.js install    → install pre-built APK onto connected device
 *   node setup.js run        → install APK + launch app on device
 *   node setup.js deploy     → init + build + install + launch (full pipeline)
 *   node setup.js uninstall  → uninstall app from device
 *   node setup.js logcat     → tail logcat filtered to MobileFTP only
 *
 * Android SDK is resolved from the ANDROID_HOME environment variable.
 * ADB device is resolved from device.ini (written by adb-setup) or auto-detected.
 *
 * Adapted from https://github.com/adityabhalsod/netspeed-monitor/blob/main/setup.js
 */

const { execSync, spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");
const readline = require("readline");

// ─── paths ───────────────────────────────────────────────────────────────────

const PROJECT_ROOT = __dirname;
const LOCAL_PROPERTIES = path.join(PROJECT_ROOT, "local.properties");
const DEVICE_INI = path.join(PROJECT_ROOT, "device.ini");

// ─── static config ───────────────────────────────────────────────────────────

const APP_PACKAGE = "com.mobileftp";
const MAIN_ACTIVITY = `${APP_PACKAGE}/.MainActivity`;
const APK_PATH = "app/build/outputs/apk/debug/app-debug.apk";
const GRADLEW = os.platform() === "win32" ? "gradlew.bat" : "./gradlew";

// ─── helpers ─────────────────────────────────────────────────────────────────

function prompt(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

function resolveAndroidSdk() {
  const fromEnv = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (fromEnv && fs.existsSync(fromEnv)) return fromEnv;

  const home = os.homedir();
  const defaults =
    os.platform() === "darwin"
      ? [path.join(home, "Library", "Android", "sdk")]
      : os.platform() === "win32"
      ? [path.join(home, "AppData", "Local", "Android", "Sdk")]
      : [path.join(home, "Android", "Sdk")];

  for (const dir of defaults) {
    if (fs.existsSync(dir)) return dir;
  }

  // Fall back to sdk.dir from device.ini if the user supplied one there
  const fromIni = readSdkDirFromIni();
  if (fromIni && fs.existsSync(fromIni)) return fromIni;

  console.error(
    "\n\x1b[31m✖  Android SDK not found.\x1b[0m\n\n" +
      "  Set the ANDROID_HOME environment variable:\n\n" +
      "    \x1b[36mexport ANDROID_HOME=$HOME/Android/Sdk\x1b[0m\n\n" +
      "  Add it to ~/.bashrc, ~/.zshrc, or your Windows env vars to make it permanent.\n" +
      "  Or set sdk.dir in device.ini.\n"
  );
  process.exit(1);
}

function readSdkDirFromIni() {
  if (!fs.existsSync(DEVICE_INI)) return null;
  const content = fs.readFileSync(DEVICE_INI, "utf-8");
  const match = content.match(/^sdk\.dir=(.+)$/m);
  return match ? match[1].trim() : null;
}

function run(cmd, label, cwd = PROJECT_ROOT) {
  console.log(`\n\x1b[36m▶  ${label}\x1b[0m`);
  console.log(`   \x1b[90m${cmd}\x1b[0m\n`);
  const result = spawnSync(cmd, { shell: true, stdio: "inherit", cwd, env: process.env });
  if (result.status !== 0) {
    console.error(`\n\x1b[31m✖  "${label}" failed (exit ${result.status})\x1b[0m`);
    process.exit(result.status ?? 1);
  }
  console.log(`\x1b[32m✔  ${label}\x1b[0m`);
}

function getConnectedDevice() {
  // First, try the saved device.ini IP:port — but verify it's actually online.
  if (fs.existsSync(DEVICE_INI)) {
    const saved = parseSavedDevice();
    if (saved) {
      const serial = `${saved.ip}:${saved.port}`;
      const ok = ensureSerialOnline(serial);
      if (ok) {
        console.log(`\x1b[32m✔  Using device: ${serial}\x1b[0m`);
        return serial;
      }
      console.log(
        `\x1b[33m⚠  Saved device ${serial} is offline — falling back to auto-detect.\x1b[0m`
      );
    }
  }

  // Auto-detect the first online device (any state ≠ "device" is rejected).
  const onlineSerial = pickFirstOnlineDevice();
  if (onlineSerial) {
    console.log(`\x1b[32m✔  Using device: ${onlineSerial}\x1b[0m`);
    return onlineSerial;
  }

  console.error(
    "\n\x1b[31m✖  No online ADB device found.\x1b[0m\n" +
      "   Make sure:\n" +
      "   1. A device is connected via USB, or wireless debugging is active\n" +
      "   2. USB debugging is enabled in Developer Options\n" +
      "   3. You authorized the computer on the device\n" +
      "   4. If wireless: re-toggle Wireless Debugging on the phone\n" +
      "   Tip: run \x1b[36mnode setup.js adb-setup\x1b[0m to re-pair.\n"
  );
  process.exit(1);
}

/**
 * Try to make `serial` come online. Sequence:
 *   1. Check current state — if already "device", we're done.
 *   2. Try `adb connect <serial>` and re-check.
 *   3. If still offline, `adb disconnect <serial>` + `adb reconnect` and re-try.
 * Returns true if the device ended up in the "device" state.
 */
function ensureSerialOnline(serial) {
  const state = adbDeviceState(serial);
  if (state === "device") return true;

  console.log(`\x1b[36m▶  Bringing ${serial} online (current state: ${state || "absent"})…\x1b[0m`);

  // Step 1: a simple reconnect often clears stale wireless sessions.
  tryExec(`adb connect ${serial}`);
  if (adbDeviceState(serial) === "device") return true;

  // Step 2: drop the dead session and try once more.
  tryExec(`adb disconnect ${serial}`);
  tryExec(`adb connect ${serial}`);
  if (adbDeviceState(serial) === "device") return true;

  // Step 3: heavyweight — restart the local ADB server and reconnect.
  console.log(`\x1b[33m⚠  Restarting ADB server to clear stale state…\x1b[0m`);
  tryExec("adb kill-server");
  tryExec("adb start-server");
  tryExec(`adb connect ${serial}`);
  return adbDeviceState(serial) === "device";
}

/**
 * Return the ADB state for a given serial: "device" | "offline" | "unauthorized" | null.
 */
function adbDeviceState(serial) {
  let output;
  try {
    output = execSync("adb devices 2>&1").toString();
  } catch (_) {
    return null;
  }
  for (const line of output.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("List of") || trimmed.startsWith("*")) continue;
    const [s, state] = trimmed.split(/\s+/);
    if (s === serial) return state || null;
  }
  return null;
}

/**
 * Find the first ADB serial currently in the "device" state.
 * Skips offline/unauthorized entries entirely.
 */
function pickFirstOnlineDevice() {
  let output;
  try {
    output = execSync("adb devices -l 2>&1").toString();
  } catch (_) {
    console.error(
      "\n\x1b[31m✖  ADB not found.\x1b[0m\n" +
        "   Make sure Android SDK platform-tools are in your PATH.\n"
    );
    process.exit(1);
  }

  for (const line of output.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("List of") || trimmed.startsWith("*")) continue;
    const cols = trimmed.split(/\s+/);
    const [serial, state] = cols;
    if (state === "device") return serial;
  }
  return null;
}

/**
 * Run a shell command silently, swallowing failures.
 * Used for adb maintenance commands where stderr is just noise.
 */
function tryExec(cmd) {
  try {
    execSync(cmd, { stdio: "pipe" });
  } catch (_) {
    /* maintenance command failed — caller verifies state separately */
  }
}

function parseSavedDevice() {
  try {
    const content = fs.readFileSync(DEVICE_INI, "utf-8");
    const ip = (content.match(/^device\.ip=(.+)$/m) || [])[1]?.trim();
    const port = (content.match(/^device\.port=(.+)$/m) || [])[1]?.trim();
    return ip && port ? { ip, port } : null;
  } catch (_) {
    return null;
  }
}

// ─── commands ────────────────────────────────────────────────────────────────

async function adbSetup() {
  console.log(
    "\n\x1b[1mWireless ADB Setup\x1b[0m\n" +
      "On your phone: \x1b[90mSettings → Developer Options → Wireless Debugging\x1b[0m\n"
  );

  console.log("\x1b[1mStep 1: Pair\x1b[0m");
  console.log("Tap \x1b[33m'Pair device with pairing code'\x1b[0m in Wireless Debugging.\n");

  const pairIp = await prompt("  Pairing IP address  : ");
  const pairPort = await prompt("  Pairing port        : ");
  const pairCode = await prompt("  Pairing code        : ");

  console.log(`\n\x1b[36m▶  adb pair ${pairIp}:${pairPort} ${pairCode}\x1b[0m\n`);
  try {
    execSync(`adb pair ${pairIp}:${pairPort} ${pairCode}`, { stdio: "inherit" });
  } catch (_) {
    /* adb pair may exit non-zero on some ADB versions even on success — checked below */
  }
  console.log("\x1b[32m✔  Pairing step done\x1b[0m");

  console.log(
    "\n\x1b[1mStep 2: Connect\x1b[0m\n" +
      "Use the IP and port shown under \x1b[33m'Wireless Debugging'\x1b[0m (not the pairing port).\n"
  );

  const connectIp = await prompt("  Connect IP address  : ");
  const connectPort = await prompt("  Connect port        : ");

  console.log(`\n\x1b[36m▶  adb connect ${connectIp}:${connectPort}\x1b[0m\n`);
  try {
    execSync(`adb connect ${connectIp}:${connectPort}`, { stdio: "inherit" });
  } catch (err) {
    console.error(
      `\n\x1b[31m✖  adb connect failed: ${err.message}\x1b[0m\n` +
        "   Check that your phone is on the same Wi-Fi network.\n"
    );
    process.exit(1);
  }
  console.log("\x1b[32m✔  Connected\x1b[0m");

  const iniContent =
    "# ─── ADB Wireless Device ────────────────────────────────────────────────\n" +
    "# Auto-generated by: node setup.js adb-setup\n" +
    "# DO NOT commit this file — it is git-ignored.\n" +
    "# ──────────────────────────────────────────────────────────────────────\n" +
    "\n" +
    "# Device IP address for ADB wireless debugging\n" +
    `device.ip=${connectIp}\n` +
    "\n" +
    "# ADB wireless debugging port (shown in Settings → Wireless Debugging)\n" +
    `device.port=${connectPort}\n`;

  fs.writeFileSync(DEVICE_INI, iniContent, "utf-8");
  console.log(`\n\x1b[32m✔  Saved connection to device.ini\x1b[0m`);
  console.log(`   device.ip   = ${connectIp}`);
  console.log(`   device.port = ${connectPort}`);
  console.log(`\n   \x1b[90mRun: \x1b[36mnode setup.js deploy\x1b[0m\n`);
}

function init() {
  const sdkDir = resolveAndroidSdk();

  // Properties files require backslashes and ':' to be escaped on Windows.
  const escapedSdkDir = sdkDir.replace(/\\/g, "\\\\").replace(/:/g, "\\:");
  const content =
    "## Auto-generated by setup.js — do not edit manually.\n" +
    "## Run: node setup.js init\n" +
    "\n" +
    "# Path to the Android SDK installation\n" +
    `sdk.dir=${escapedSdkDir}\n`;

  fs.writeFileSync(LOCAL_PROPERTIES, content, "utf-8");
  console.log(`\x1b[32m✔  Generated local.properties\x1b[0m`);
  console.log(`   sdk.dir = ${sdkDir}`);
  console.log(`\n   \x1b[90mYou can now run: \x1b[36mnode setup.js deploy\x1b[0m\n`);
}

function ensureJavaHome() {
  const javaBin = os.platform() === "win32" ? "java.exe" : "java";

  if (
    process.env.JAVA_HOME &&
    fs.existsSync(path.join(process.env.JAVA_HOME, "bin", javaBin))
  ) {
    return process.env.JAVA_HOME;
  }

  const candidates =
    os.platform() === "win32"
      ? [
          "C:\\Program Files\\Android\\Android Studio\\jbr",
          "C:\\Program Files\\Eclipse Adoptium",
          "C:\\Program Files\\Java",
        ]
      : os.platform() === "darwin"
      ? ["/Applications/Android Studio.app/Contents/jbr/Contents/Home"]
      : ["/opt/android-studio/jbr", "/usr/lib/jvm"];

  for (const base of candidates) {
    if (!fs.existsSync(base)) continue;
    if (fs.existsSync(path.join(base, "bin", javaBin))) {
      process.env.JAVA_HOME = base;
      return base;
    }
    try {
      for (const entry of fs.readdirSync(base)) {
        const candidate = path.join(base, entry);
        if (fs.existsSync(path.join(candidate, "bin", javaBin))) {
          process.env.JAVA_HOME = candidate;
          return candidate;
        }
      }
    } catch (_) { /* not readable — skip */ }
  }

  return null;
}

// Gradle version used by the wrapper (matches gradle-wrapper.properties)
const GRADLE_VERSION = "8.7";
const GRADLE_TAG = `v${GRADLE_VERSION}.0`;
const GRADLE_GH_RAW = `https://raw.githubusercontent.com/gradle/gradle/${GRADLE_TAG}`;

/**
 * Download a file over HTTPS to a local path, following redirects.
 * Returns a Promise that resolves when the file is fully written.
 */
function downloadFile(url, destPath) {
  // Lazy-require so the dependency is only paid when this function is used.
  const https = require("https");
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(destPath), { recursive: true });
    const tmpPath = `${destPath}.part`;
    const file = fs.createWriteStream(tmpPath);

    const handleResponse = (res) => {
      // Follow redirects (GitHub raw can 302 to objects.githubusercontent.com)
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        https.get(res.headers.location, handleResponse).on("error", onError);
        return;
      }
      if (res.statusCode !== 200) {
        onError(new Error(`HTTP ${res.statusCode} for ${url}`));
        return;
      }
      res.pipe(file);
      file.on("finish", () => {
        file.close(() => {
          fs.renameSync(tmpPath, destPath);
          resolve(destPath);
        });
      });
    };

    const onError = (err) => {
      file.close();
      try { fs.unlinkSync(tmpPath); } catch (_) { /* ignore */ }
      reject(err);
    };

    https.get(url, handleResponse).on("error", onError);
  });
}

/**
 * Ensure gradle-wrapper.jar exists; download it from the official Gradle
 * GitHub tag if missing. Also fetches gradlew / gradlew.bat if absent.
 */
async function ensureGradleWrapper() {
  const wrapperJar = path.join(PROJECT_ROOT, "gradle", "wrapper", "gradle-wrapper.jar");
  const wrapperProps = path.join(PROJECT_ROOT, "gradle", "wrapper", "gradle-wrapper.properties");
  const gradlewSh = path.join(PROJECT_ROOT, "gradlew");
  const gradlewBat = path.join(PROJECT_ROOT, "gradlew.bat");

  // Make sure the wrapper.properties points at a real Gradle distribution.
  if (!fs.existsSync(wrapperProps)) {
    fs.mkdirSync(path.dirname(wrapperProps), { recursive: true });
    fs.writeFileSync(
      wrapperProps,
      "distributionBase=GRADLE_USER_HOME\n" +
        "distributionPath=wrapper/dists\n" +
        `distributionUrl=https\\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip\n` +
        "networkTimeout=10000\n" +
        "validateDistributionUrl=true\n" +
        "zipStoreBase=GRADLE_USER_HOME\n" +
        "zipStorePath=wrapper/dists\n",
      "utf-8"
    );
  }

  const downloads = [];
  if (!fs.existsSync(wrapperJar)) {
    downloads.push({
      url: `${GRADLE_GH_RAW}/gradle/wrapper/gradle-wrapper.jar`,
      dest: wrapperJar,
      label: "gradle-wrapper.jar",
    });
  }
  if (!fs.existsSync(gradlewSh)) {
    downloads.push({
      url: `${GRADLE_GH_RAW}/gradlew`,
      dest: gradlewSh,
      label: "gradlew",
    });
  }
  if (!fs.existsSync(gradlewBat)) {
    downloads.push({
      url: `${GRADLE_GH_RAW}/gradlew.bat`,
      dest: gradlewBat,
      label: "gradlew.bat",
    });
  }

  if (downloads.length === 0) return;

  console.log(
    `\x1b[33m⚠  Gradle wrapper missing — downloading ${downloads.length} file(s) from gradle/gradle@${GRADLE_TAG}…\x1b[0m`
  );
  for (const d of downloads) {
    process.stdout.write(`   ↓ ${d.label}… `);
    try {
      await downloadFile(d.url, d.dest);
      // Make gradlew executable on POSIX (chmod is a no-op on Windows)
      if (d.label === "gradlew" && os.platform() !== "win32") {
        fs.chmodSync(d.dest, 0o755);
      }
      console.log("\x1b[32mok\x1b[0m");
    } catch (err) {
      console.log(`\x1b[31mfailed\x1b[0m (${err.message})`);
      console.error(
        "\n\x1b[31m✖  Could not auto-download the Gradle wrapper.\x1b[0m\n" +
          "   Check your internet connection or proxy settings.\n" +
          "   Manual fallback: open the project in Android Studio once,\n" +
          "   or run: \x1b[36mgradle wrapper --gradle-version " +
          GRADLE_VERSION +
          " --distribution-type bin\x1b[0m\n"
      );
      process.exit(1);
    }
  }
  console.log(`\x1b[32m✔  Gradle wrapper provisioned\x1b[0m`);
}

async function build() {
  ensureLocalProperties();
  await ensureGradleWrapper();
  const javaHome = ensureJavaHome();
  if (javaHome) {
    console.log(`\x1b[90m   JAVA_HOME = ${javaHome}\x1b[0m`);
  }
  run(`${GRADLEW} assembleDebug`, "Gradle assembleDebug");
  console.log(`\n\x1b[32m✔  APK ready at: ${APK_PATH}\x1b[0m`);
}

async function install() {
  if (!fs.existsSync(path.join(PROJECT_ROOT, APK_PATH))) {
    console.log(`\x1b[33m⚠  APK not found at ${APK_PATH} — building first…\x1b[0m`);
    await build();
  }
  const serial = getConnectedDevice();
  const adb = `adb -s ${serial}`;
  run(`${adb} install -r ${APK_PATH}`, "Install APK");
  console.log(`\n\x1b[32m✔  APK installed on ${serial}\x1b[0m\n`);
}

async function runOnDevice() {
  if (!fs.existsSync(path.join(PROJECT_ROOT, APK_PATH))) {
    console.log(`\x1b[33m⚠  APK not found at ${APK_PATH} — building first…\x1b[0m`);
    await build();
  }
  const serial = getConnectedDevice();
  const adb = `adb -s ${serial}`;
  run(`${adb} install -r ${APK_PATH}`, "Install APK");
  run(`${adb} shell am start -n ${MAIN_ACTIVITY}`, "Launch MobileFTP");
  console.log(`\n\x1b[32m🚀  App running on ${serial}\x1b[0m\n`);
}

function uninstall() {
  const serial = getConnectedDevice();
  const adb = `adb -s ${serial}`;
  run(`${adb} uninstall ${APP_PACKAGE}`, `Uninstall ${APP_PACKAGE}`);
  console.log(`\n\x1b[32m✔  App uninstalled from ${serial}\x1b[0m\n`);
}

function logcat() {
  const serial = getConnectedDevice();
  console.log(
    `\n\x1b[36m▶  Tailing logcat for ${APP_PACKAGE} (Ctrl+C to stop)…\x1b[0m\n`
  );
  // Pipe logcat through grep/findstr on the package name; runs until Ctrl+C.
  const cmd =
    os.platform() === "win32"
      ? `adb -s ${serial} logcat | findstr /C:"${APP_PACKAGE}"`
      : `adb -s ${serial} logcat | grep --line-buffered -i "${APP_PACKAGE}"`;
  spawnSync(cmd, { shell: true, stdio: "inherit", env: process.env });
}

/**
 * Force-recover a wireless ADB session without going through full re-pairing.
 * Tries the saved device.ini IP:port first, then any persistent mDNS entry
 * (adb-XXX._adb-tls-connect._tcp), then auto-detects any online device.
 */
function reconnect() {
  // Always start with a clean ADB server — clears stale "offline" entries.
  console.log(`\x1b[36m▶  Restarting ADB server…\x1b[0m`);
  tryExec("adb kill-server");
  tryExec("adb start-server");

  // Step 1: saved device.ini IP:port
  if (fs.existsSync(DEVICE_INI)) {
    const saved = parseSavedDevice();
    if (saved) {
      const serial = `${saved.ip}:${saved.port}`;
      console.log(`\x1b[36m▶  Trying saved device ${serial}…\x1b[0m`);
      tryExec(`adb connect ${serial}`);
      if (adbDeviceState(serial) === "device") {
        console.log(`\x1b[32m✔  Reconnected: ${serial}\x1b[0m`);
        return;
      }
      console.log(`\x1b[33m⚠  ${serial} still ${adbDeviceState(serial) || "absent"}.\x1b[0m`);
    }
  }

  // Step 2: any device that came back online via mDNS or USB
  const online = pickFirstOnlineDevice();
  if (online) {
    console.log(`\x1b[32m✔  Online device detected: ${online}\x1b[0m`);
    return;
  }

  console.error(
    "\n\x1b[31m✖  Could not bring any device online.\x1b[0m\n" +
      "   On your phone, toggle Wireless Debugging off and on, then check the IP & port.\n" +
      "   If the port has changed, run: \x1b[36mnode setup.js adb-setup\x1b[0m\n"
  );
  process.exit(1);
}

async function deploy() {
  ensureLocalProperties();
  await build();
  await runOnDevice();
}

function ensureLocalProperties() {
  if (!fs.existsSync(LOCAL_PROPERTIES)) {
    console.log(
      "\x1b[33m⚠  local.properties not found — generating from ANDROID_HOME…\x1b[0m"
    );
    init();
  }
}

// ─── entry point ─────────────────────────────────────────────────────────────

const command = process.argv[2];
const commands = {
  "adb-setup": adbSetup,
  reconnect,
  init,
  build,
  install,
  run: runOnDevice,
  deploy,
  uninstall,
  logcat,
};

if (!command || !commands[command]) {
  console.log(
    "\n\x1b[1mMobileFTP — setup helper\x1b[0m\n\n" +
      "  \x1b[36mnode setup.js adb-setup\x1b[0m   Pair + connect wireless ADB, save to device.ini\n" +
      "  \x1b[36mnode setup.js reconnect\x1b[0m   Recover an offline wireless ADB session\n" +
      "  \x1b[36mnode setup.js init\x1b[0m        Generate local.properties from ANDROID_HOME\n" +
      "  \x1b[36mnode setup.js build\x1b[0m       Compile & produce debug APK\n" +
      "  \x1b[36mnode setup.js install\x1b[0m     Install pre-built APK on device\n" +
      "  \x1b[36mnode setup.js run\x1b[0m         Install APK on device + launch app\n" +
      "  \x1b[36mnode setup.js deploy\x1b[0m      Build + install + launch (full pipeline)\n" +
      "  \x1b[36mnode setup.js uninstall\x1b[0m   Uninstall app from device\n" +
      "  \x1b[36mnode setup.js logcat\x1b[0m      Tail logcat filtered to MobileFTP\n"
  );
  process.exit(0);
}

Promise.resolve(commands[command]()).catch((err) => {
  console.error(`\n\x1b[31m✖  ${err.message}\x1b[0m`);
  process.exit(1);
});
