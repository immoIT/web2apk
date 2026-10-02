# web2apk

A configurable Android WebView wrapper that can be built locally or by GitHub Actions.

## Custom folder

All manual customization lives under `web2apk/custom/`:

```text
custom/
├── icon.png                 # upload your own app icon (optional)
├── generated-a.js           # optional JS file
├── generated-b.js           # optional JS file
└── scripts/
    ├── 01-startup.js
    └── 02-custom-ui.js
```

### App icon

Upload an icon as one of:

- `custom/icon.png`
- `custom/icon.webp`
- `custom/icon.jpg`
- `custom/icon.jpeg`

The first supported icon found is used automatically. If no icon is present, a built-in fallback icon is generated.

### Custom WebView scripts

You can deploy one or multiple generated `.js` files into the APK.

Use the GitHub Actions variable:

```text
CUSTOM_JS_FILES=generated-a.js,generated-b.js
```

The paths are relative to `custom/`, so this also works:

```text
CUSTOM_JS_FILES=generated/app.js,generated/ui.js,scripts/extra.js
```

Each selected file is copied into the APK under `assets/custom-js/` and injected after the configured website finishes loading, in the exact order listed in `CUSTOM_JS_FILES`.

If `CUSTOM_JS_FILES` is empty, the backward-compatible behavior is used: every `.js` file under `custom/scripts/` is included in filename order.

Set:

```text
ENABLE_CUSTOM_SCRIPTS=false
```

to disable custom script injection.

## Default configuration

Edit:

```text
config/defaults.properties
```

This is the single project-level defaults file. It contains the defaults for URL, app metadata, WebView behavior, fullscreen mode, custom scripts, and Android permissions.

Precedence is:

1. GitHub Actions repository variable / Gradle `-P` property
2. `config/defaults.properties`
3. hard-coded safety fallback in `app/build.gradle.kts`

For a local build, for example:

```bash
gradle :app:assembleDebug -PWEB_URL=https://example.com -PAPP_NAME="My App"
```

## GitHub Actions variables

GitHub Actions reads repository variables from:

**Settings → Secrets and variables → Actions → Variables**

If a variable is not set, its value is left empty and Gradle automatically falls back to `config/defaults.properties`.

Supported variables include:

### App

- `WEB_URL`
- `APP_NAME`
- `PACKAGE_NAME`
- `VERSION_NAME`
- `VERSION_CODE`
- `MIN_SDK`
- `TARGET_SDK`
- `ORIENTATION`

### WebView

- `ENABLE_JAVASCRIPT`
- `ENABLE_DOM_STORAGE`
- `ENABLE_PULL_TO_REFRESH`
- `ALLOW_EXTERNAL_LINKS`
- `ENABLE_DOWNLOADS`
- `ENABLE_FILE_UPLOAD`
- `ENABLE_FULLSCREEN_VIDEO`
- `ENABLE_FULLSCREEN`
- `ENABLE_CUSTOM_SCRIPTS`
- `CUSTOM_JS_FILES` — comma-separated `.js` paths relative to `custom/`
- `ENABLE_LOGGING`

### Permissions

- `PERMISSION_INTERNET`
- `PERMISSION_CAMERA`
- `PERMISSION_MICROPHONE`
- `PERMISSION_LOCATION`
- `PERMISSION_NOTIFICATIONS`
- `PERMISSION_STORAGE`
- `PERMISSION_CONTACTS`
- `PERMISSION_PHONE`
- `PERMISSION_CALENDAR`
- `PERMISSION_BLUETOOTH`
- `PERMISSION_VIBRATE`
- `PERMISSION_NFC`

The workflow passes GitHub variables to Gradle with `-P`, so a GitHub variable overrides the project defaults without requiring changes to the workflow.

## Build

Push the repository to GitHub and run:

**Actions → Build Website APK**

The generated debug APK is uploaded as an artifact.

## Important

This project wraps the configured website in Android WebView; it does not convert website source code into native Android UI.

The custom script bridge is injected only on the configured trusted host, matching the existing clipboard integration's host check.

## Diagnostics

The GitHub workflow keeps detailed Gradle, resource, Kotlin, APK, and environment diagnostics under `build-logs/` and uploads the diagnostic bundle even when a build fails.

### Configuration defaults and GitHub Actions variables

`config/defaults.properties` provides fallback values for local and CI builds.
GitHub Actions repository variables with the same names override those defaults
when they are non-empty. Empty/unset variables fall back to the defaults file,
so booleans such as `ALLOW_EXTERNAL_LINKS` never become an empty string.

### Gradle Kotlin DSL compatibility

The configuration loader uses an explicit `java.util.Properties` import and explicit Kotlin lambda parameters so the Gradle Kotlin DSL compiles correctly.


## Android TV compatibility

This version declares Android TV/Leanback support, does not require a touchscreen, disables touch-only pull-to-refresh on TV devices, gives the WebView initial D-pad focus, and adds spatial D-pad/OK navigation for common links, buttons, and form controls.

Build with Android Studio/Gradle and install the resulting APK on the TV.

## Android TV build and installation

The project includes explicit Android TV compatibility fixes. In particular, optional
hardware features are marked `required=false` so permissions such as camera, microphone,
telephony, Bluetooth, NFC, and location do not accidentally make a TV without that hardware
look incompatible. A TV launcher banner is also included.

The GitHub Actions workflow builds:

- `*-debug.apk` for development/testing.
- `*-tv-compatible-release.apk` as a **signed test release** using the standard debug signing
  key, so it can be sideloaded without a private production keystore.

For production distribution, replace the debug signing configuration with your own release
keystore before publishing.

To diagnose a TV installation error locally:

```bash
./tools/install-on-android-tv.sh path/to/app-release.apk
```

If Android TV still reports `App not installed`, use the exact `Failure [INSTALL_FAILED_...]`
line printed by ADB. A generic TV dialog does not contain enough information to distinguish a
signature conflict, version downgrade, ABI mismatch, or another PackageManager error.
