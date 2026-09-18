# Website To APK — GitHub Variables

This repository wraps a website in an Android WebView and builds the APK through GitHub Actions.

## Configuration

**All configuration comes from GitHub Actions repository variables.**

Go to:

**GitHub → Repository → Settings → Secrets and variables → Actions → Variables**

The repository variables are optional because the workflow includes defaults.

Create these repository variables:

### Main
- `WEB_URL`
- `APP_NAME`
- `APP_ICON_URL`
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
- `ENABLE_LOGGING`

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

Every setting has a project default in the workflow. If a GitHub Actions repository variable with the same name is set, that GitHub variable overrides the project default. There is no `config.yml` configuration path.

## Build

Push to GitHub, then open:

**Actions → Build Website APK**

The generated APK is uploaded as the `website-to-apk` artifact.

`-P` in the Gradle command is intentional: it passes each GitHub variable to Gradle as a project property.

## Notes

This is a WebView wrapper, not a native conversion of website source code.

### Project defaults

- `WEB_URL` = `https://example.com`
- `APP_NAME` = `BoltDownloader`
- `APP_ICON_URL` = empty (uses the bundled default icon)
- `PACKAGE_NAME` = `com.boltdownloader.app`
- `VERSION_NAME` = `1.0.0`
- `VERSION_CODE` = `1`
- `MIN_SDK` = `24`
- `TARGET_SDK` = `35`
- `ORIENTATION` = `unspecified`
- `ENABLE_JAVASCRIPT` = `true`
- `ENABLE_DOM_STORAGE` = `true`
- `ENABLE_PULL_TO_REFRESH` = `true`
- `ALLOW_EXTERNAL_LINKS` = `true`
- `ENABLE_DOWNLOADS` = `true`
- `ENABLE_FILE_UPLOAD` = `true`
- `ENABLE_FULLSCREEN_VIDEO` = `true`
- `ENABLE_LOGGING` = `true`
- `PERMISSION_INTERNET` = `true`
- `PERMISSION_CAMERA` = `false`
- `PERMISSION_MICROPHONE` = `false`
- `PERMISSION_LOCATION` = `false`
- `PERMISSION_NOTIFICATIONS` = `false`
- `PERMISSION_STORAGE` = `false`
- `PERMISSION_CONTACTS` = `false`
- `PERMISSION_PHONE` = `false`
- `PERMISSION_CALENDAR` = `false`
- `PERMISSION_BLUETOOTH` = `false`
- `PERMISSION_VIBRATE` = `true`
- `PERMISSION_NFC` = `false`

Replace `WEB_URL` with the actual BoltDownloader website URL, either in GitHub Actions Variables or directly in the workflow default.

`APP_ICON_URL` should be a direct HTTP(S) link to a PNG, JPG, or GIF image. During the GitHub Actions build, the image is downloaded, center-cropped to a square, resized to 512×512, and embedded into the APK as the launcher icon. If `APP_ICON_URL` is empty, the bundled default icon is used.

## Build diagnostics

GitHub Actions now captures a separate log for each major build step under the `build-logs/` directory. The workflow also uploads a `website-to-apk-build-logs` artifact on every run, including failed runs, containing Gradle logs and diagnostics.

The workflow uses `set -Eeuo pipefail`, `--stacktrace`, `--info`, and `--warning-mode all` so command failures and Gradle errors are visible in the step output. A final failure summary prints the last detected `ERROR`, `FAILURE`, `Exception`, `Caused by`, and warning lines.

The Android app also logs WebView page loads, JavaScript console messages, HTTP/resource/SSL errors, downloads, file chooser failures, external URI handling, and permission failures using the `WebsiteToAPK` Logcat tag. Set `ENABLE_LOGGING=false` to disable these runtime logs.
