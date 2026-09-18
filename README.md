# Website To APK — Optional Variables Edition

This repository wraps a website in an Android WebView and builds the APK through GitHub Actions.

## Optional variables

You do NOT have to create any GitHub Actions variables. Built-in defaults are used.

If a repository variable exists, it overrides the default.

### Main variables

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

### Android permissions

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

All permission variables default to `false`, except Internet which defaults to `true`.

## Configure

You can either:

1. Do nothing and use defaults.
2. Add GitHub repository variables under Settings → Secrets and variables → Actions → Variables.
3. Use `config.yml` as a human-readable configuration reference.

The workflow always has fallback values, so missing variables do not break the build.

## Build

Push to GitHub. Then open:

Actions → Build Website APK

Download the `website-to-apk` artifact.

You can also use **Run workflow** and optionally supply a website URL.

## Important

This is a WebView wrapper, not a native conversion of website source code.

Website clicks, JavaScript interactions, forms, SPA navigation, and normal HTTP/HTTPS links work through WebView. Camera, microphone, location and other device features require both Android permission configuration and support from the website.

For Play Store production releases, configure app signing and review the permissions you enable.
