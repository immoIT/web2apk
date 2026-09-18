# Loading and clipboard fixes

## Fixed
- Removed the unused native `ProgressBar` overlay that remained visible during the first page load.
- Kept pull-to-refresh behavior unchanged; the refresh spinner only appears for an actual pull-to-refresh.
- Made the Android clipboard bridge safer and added Logcat diagnostics.
- Re-injects the clipboard bridge shortly after page load because SPA/framework code can replace `navigator.clipboard`.
- Added a click handler for Paste buttons/icons (button, role=button, input button/submit, and links whose text/label/action contains `paste`).
- Paste reads the current Android clipboard at click time, so copied plain text and copied URLs can be inserted.
- Remembers the last focused editable element when the Paste button takes focus.
- Uses the native input value setter and dispatches `input`/`change`, improving compatibility with controlled React/Vue-style fields.
- Clipboard access remains restricted to the configured `WEB_URL` host.

## Diagnostics
Look for the `WebsiteToAPK` Logcat tag. Clipboard failures, empty clipboard state, WebView errors, HTTP errors, SSL errors, and JavaScript console errors are logged when `ENABLE_LOGGING=true`.
