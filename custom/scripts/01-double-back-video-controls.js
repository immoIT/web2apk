// Show HTML5 video controls when the Android back button is pressed twice quickly.
// The Android wrapper dispatches the "web2apk-double-back" event on the WebView.
(function () {
    if (window.__web2apkDoubleBackVideoControlsInstalled) return;
    window.__web2apkDoubleBackVideoControlsInstalled = true;

    function showVideoControls() {
        var videos = Array.prototype.slice.call(document.querySelectorAll('video'));

        videos.forEach(function (video) {
            try {
                video.controls = true;
                video.setAttribute('controls', '');
            } catch (_) {}
        });

        // Keep controls enabled for videos added later by an SPA/player.
        try {
            if (!window.__web2apkVideoObserver) {
                var observer = new MutationObserver(function () {
                    document.querySelectorAll('video').forEach(function (video) {
                        try {
                            video.controls = true;
                            video.setAttribute('controls', '');
                        } catch (_) {}
                    });
                });

                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true
                });
                window.__web2apkVideoObserver = observer;
            }
        } catch (_) {}

        // Give the current video a chance to display its controls immediately.
        var activeVideo = document.querySelector('video');
        if (activeVideo) {
            try {
                activeVideo.focus();
                activeVideo.dispatchEvent(new MouseEvent('mousemove', {
                    bubbles: true,
                    cancelable: true,
                    view: window
                }));
            } catch (_) {}
        }
    }

    window.addEventListener('web2apk-double-back', showVideoControls, false);

    // Optional public function for the page itself.
    window.web2apkShowVideoControls = showVideoControls;
})();
