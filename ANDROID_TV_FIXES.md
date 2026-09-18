# Android TV compatibility fixes

This project is intended to produce one APK that can run on phones/tablets and Android TV.

## What was fixed

- Declared touchscreen and Leanback as optional rather than required.
- Explicitly declared camera, autofocus, microphone, location, telephony, Bluetooth, NFC,
  and Wi-Fi hardware as optional. This avoids implicit hardware requirements from optional
  permissions causing TV compatibility filtering/install failures.
- Added an Android TV launcher banner.
- Kept the normal `LAUNCHER` entry point while also exposing `LEANBACK_LAUNCHER`.
- TV disables pull-to-refresh and includes D-pad focus navigation in the WebView.
- Added release build support in CI so both debug and release artifacts can be tested.

## If a TV still says "App not installed"

The exact PackageManager failure matters. Use:

```bash
adb install -r app-debug.apk
```

or, for a release APK:

```bash
adb install -r app-release-unsigned.apk
```

Common remaining causes are an existing copy signed with a different key or a version-code
that is newer on the TV. In those cases the source APK itself is not the incompatibility.
