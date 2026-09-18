# Custom WebView scripts

Put `.js` files in this folder. All JavaScript files are combined in filename order and
injected after each trusted page finishes loading.

Examples:
- `01-startup.js`
- `02-buttons.js`

Keep scripts self-contained and test them in a normal browser first.


### Double-back video controls

`01-double-back-video-controls.js` listens for the Android wrapper's
`web2apk-double-back` event. Pressing the Android Back button twice within 600 ms
dispatches that event and the script enables native HTML5 `<video>` controls on
the current page. The normal single-back history/exit behavior is unchanged.
