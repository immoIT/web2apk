# web2apk custom files

This folder is the manual customization area.

## App icon
Upload one of these files directly here:
- `icon.png`
- `icon.webp`
- `icon.jpg`
- `icon.jpeg`

The first matching file is used automatically as the app icon. If no icon is uploaded,
the project uses a built-in fallback icon.

## WebView scripts
Put custom JavaScript in `scripts/` as `.js` files. They are combined and injected after
the configured website finishes loading. Set `ENABLE_CUSTOM_SCRIPTS=false` to disable them.

Do not edit generated files under `app/build/`.
