/* =========================================================
   02-curl-player-controls.js
   Overrides the curl player's control show/hide behaviour.

   Rules
   -----
   1. Any activity (mouse move, touch, D-pad / key press, focus change)
      inside the player shows the controls and restarts a 5 s timer.
      While the user keeps "hovering"/navigating, the controls stay.
   2. When that activity stops, the controls hide after HIDE_DELAY_MS.
   3. Controls never auto-hide while the video is paused or a popup
      menu (audio / subtitles / quality) is open.
   4. A single BACK press:
        - controls hidden  -> show them (and start the timer)
        - controls visible -> exit rotate / fullscreen, close an open
                              menu, otherwise close the player
   5. While the controls are hidden, OK/Enter only wakes them up
      (prevents pressing an invisible button by accident).

   Put this file in web2apk/custom/scripts/. It must replace
   01-double-back-video-controls.js (or use
   CUSTOM_JS_FILES=scripts/02-curl-player-controls.js), because both
   scripts handle BACK and would fight each other.
   ========================================================= */
(function () {
    'use strict';

    // web2apk injects on every onPageFinished; install only once per document.
    if (window.__curlPlayerControlsInstalled) return;
    window.__curlPlayerControlsInstalled = true;

    /* ---------------- settings ---------------- */
    var HIDE_DELAY_MS = 5000;                    // idle time before hiding
    var SHOW_ON_PLAY = true;                     // show controls when playback starts
    var CLOSE_PLAYER_ON_BACK_WHEN_VISIBLE = true;
    var SWALLOW_OK_WHEN_HIDDEN = true;
    var ACTIVITY_THROTTLE_MS = 150;

    /* ---------------- helpers ---------------- */
    function $(id) { return document.getElementById(id); }

    function playerOpen() {
        var m = $('playerModal');
        return !!(m && m.classList.contains('show'));
    }
    function skeletonVisible() {
        var s = $('playerSkeleton');
        return !!(s && !s.classList.contains('hidden'));
    }
    function bufferingVisible() {
        var b = $('bufferingIcon');
        return !!(b && b.style.display === 'block');
    }
    function suppressed() { return skeletonVisible() || bufferingVisible(); }
    function menuOpen() { return !!document.querySelector('.popup-menu.active'); }
    function paused() {
        var v = $('video');
        return !v || v.paused;
    }
    function controlsHidden() {
        var c = $('controls');
        return !!(c && c.classList.contains('ui-hidden'));
    }
    function isTyping(el) {
        if (!el || !el.tagName) return false;
        var t = el.tagName.toLowerCase();
        return t === 'textarea' || el.isContentEditable ||
            (t === 'input' && !/^(range|button|checkbox|radio|submit)$/i.test(el.type || ''));
    }

    /* ---------------- show / hide core ---------------- */
    var UI_IDS = ['controls', 'videoTitle', 'centerPlayBtn', 'closePlayerBtn'];
    var visible = true;   // what THIS script currently wants
    var timer = null;

    function apply(show) {
        visible = show;
        UI_IDS.forEach(function (id) {
            var el = $(id);
            if (el) el.classList.toggle('ui-hidden', !show);
        });
        var w = $('wrapper');
        if (w) w.style.cursor = show ? 'default' : 'none';
    }

    function arm() {
        clearTimeout(timer);
        timer = null;
        if (!playerOpen() || paused()) return;
        timer = setTimeout(function () {
            timer = null;
            tryHide();
        }, HIDE_DELAY_MS);
    }

    function tryHide() {
        if (!playerOpen() || paused()) return;
        if (menuOpen()) { arm(); return; }   // check again later
        apply(false);
    }

    function reveal() {
        if (!playerOpen() || skeletonVisible()) return;
        apply(true);
        arm();
    }

    /* ---------------- activity = "hovering" ---------------- */
    var lastPing = 0;
    function activity() {
        if (!playerOpen()) return;
        var now = Date.now();
        if (visible && now - lastPing < ACTIVITY_THROTTLE_MS) return;
        lastPing = now;
        reveal();
    }

    function insideWrapper(e) {
        var w = $('wrapper');
        return !!(w && e.target && w.contains(e.target));
    }

    ['mousemove', 'mousedown', 'pointerdown', 'touchstart', 'touchmove', 'wheel']
        .forEach(function (type) {
            window.addEventListener(type, function (e) {
                if (insideWrapper(e)) activity();
            }, { capture: true, passive: true });
        });

    window.addEventListener('focusin', function (e) {
        var m = $('playerModal');
        if (m && e.target && m.contains(e.target)) activity();
    }, true);

    /* ---------------- BACK handling ---------------- */
    var lastBack = 0;

    function handleBack() {
        if (!playerOpen()) return false;

        var now = Date.now();
        if (now - lastBack < 250) return true;   // ignore duplicate events
        lastBack = now;

        // 1) hidden -> just show
        if (controlsHidden() && !skeletonVisible()) {
            reveal();
            return true;
        }

        // 2) visible -> leave the innermost state first
        if (typeof isRotated !== 'undefined' && isRotated && typeof setRotationState === 'function') {
            setRotationState(false);
            reveal();
            return true;
        }
        if (typeof isOverlay !== 'undefined' && isOverlay && typeof toggleOverlay === 'function') {
            toggleOverlay(false);
            reveal();
            return true;
        }
        if (menuOpen()) {
            Array.prototype.forEach.call(document.querySelectorAll('.popup-menu.active'), function (m) {
                m.classList.remove('active');
            });
            reveal();
            return true;
        }

        // 3) nothing left to leave
        if (CLOSE_PLAYER_ON_BACK_WHEN_VISIBLE) {
            var closeBtn = $('closePlayerBtn');
            if (closeBtn) closeBtn.click();
            return true;
        }
        reveal();
        return true;
    }

    // Android wrapper: MainActivity calls window.__androidCloseCurrentWebState()
    // on BACK and skips its own history/exit logic when it returns true.
    // The TV navigation script assigns this function AFTER we run, so intercept
    // the assignment and always keep our handler in front of it.
    (function hookAndroidBack() {
        var original = window.__androidCloseCurrentWebState;
        var front = function () {
            if (playerOpen()) return handleBack();
            return typeof original === 'function' ? original.apply(this, arguments) : false;
        };
        try {
            Object.defineProperty(window, '__androidCloseCurrentWebState', {
                configurable: true,
                enumerable: true,
                get: function () { return front; },
                set: function (fn) { original = fn; }
            });
        } catch (e) {
            console.error('curl player: could not hook Android back', e);
        }
    })();

    // Keyboard / remotes that deliver BACK as a key event.
    function isBackKey(e) {
        return e.key === 'Escape' || e.key === 'Back' || e.key === 'GoBack' ||
            e.keyCode === 27 || e.keyCode === 4 || e.keyCode === 461 || e.keyCode === 10009 ||
            (e.keyCode === 8 && !isTyping(e.target));
    }

    window.addEventListener('keydown', function (e) {
        if (!playerOpen()) return;

        if (isBackKey(e)) {
            // Runs before curl's own document-level handler, so it cannot
            // see the controls as "already visible" and exit fullscreen early.
            e.preventDefault();
            e.stopImmediatePropagation();
            handleBack();
            return;
        }

        if (SWALLOW_OK_WHEN_HIDDEN && controlsHidden() && !skeletonVisible() &&
            (e.key === 'Enter' || e.keyCode === 13) && !isTyping(e.target)) {
            e.preventDefault();
            e.stopImmediatePropagation();
            reveal();
            return;
        }

        activity();
    }, true);

    /* ---------------- keep curl.js from fighting us ---------------- */
    // curl's own timer hides the controls 2-5 s after ITS last event and it
    // force-hides on 'play'. If that happens while we still want them visible
    // (and the player is not loading/buffering) undo it immediately.
    // The observer callback runs before the next paint, so there is no flicker.
    function watchControls() {
        var c = $('controls');
        if (!c) return false;
        new MutationObserver(function () {
            if (!playerOpen()) return;
            var hidden = c.classList.contains('ui-hidden');
            if (hidden && visible) {
                if (suppressed()) {
                    visible = false;
                    clearTimeout(timer);
                    timer = null;
                } else {
                    apply(true);
                }
            } else if (!hidden && !visible) {
                visible = true;   // curl showed them (mouse/touch/menu close)
                arm();
            }
        }).observe(c, { attributes: true, attributeFilter: ['class'] });
        return true;
    }

    function watchPlayerLifecycle() {
        var modal = $('playerModal');
        if (modal) {
            new MutationObserver(function () {
                if (playerOpen()) {
                    reveal();
                } else {
                    clearTimeout(timer);
                    timer = null;
                    visible = true;
                }
            }).observe(modal, { attributes: true, attributeFilter: ['class'] });
        }

        // Loading finished -> show controls for a moment.
        var skel = $('playerSkeleton');
        if (skel && SHOW_ON_PLAY) {
            new MutationObserver(function () {
                if (playerOpen() && !skeletonVisible()) reveal();
            }).observe(skel, { attributes: true, attributeFilter: ['class'] });
        }

        var v = $('video');
        if (v) {
            v.addEventListener('play', function () { if (SHOW_ON_PLAY) reveal(); else arm(); });
            v.addEventListener('playing', function () { arm(); });
            v.addEventListener('pause', function () {
                clearTimeout(timer);
                timer = null;
                reveal();          // stay visible while paused
            });
            v.addEventListener('seeked', activity);
        }
    }

    watchControls();
    watchPlayerLifecycle();

    // If the player was already open when this script was injected.
    if (playerOpen()) reveal();
})();
