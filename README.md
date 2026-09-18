# Website To APK — GitHub Variables

This repository wraps a website in an Android WebView and builds the APK through GitHub Actions.

## Configuration

**All configuration comes from GitHub Actions repository variables.**

Go to:

**GitHub → Repository → Settings → Secrets and variables → Actions → Variables**

Create these repository variables:

### Main
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

The workflow validates that these variables exist before building. There is no `config.yml` configuration path.

## Build

Push to GitHub, then open:

**Actions → Build Website APK**

The generated APK is uploaded as the `website-to-apk` artifact.

`-P` in the Gradle command is intentional: it passes each GitHub variable to Gradle as a project property.

## Notes

This is a WebView wrapper, not a native conversion of website source code.
