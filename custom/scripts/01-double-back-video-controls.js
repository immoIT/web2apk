/* =========================================================
 * TV Back Button for curl video player (web2apk Android TV)  v2
 * ------------------------------------------------------------
 * - Controls show hote hi top-left me Back button aata hai
 * - 5 second baad button + controls DONO force-hide ho jate
 *   hain (TV par mouseenter/focus stuck hone se bachne ke liye)
 * - Remote ki koi bhi key/timer activity par 5s timer reset
 * - Video paused ho to controls visible rehte hain (normal)
 * ========================================================= */
(function () {
    'use strict';
    if (window.__tvBackButtonInstalled) return;
    window.__tvBackButtonInstalled = true;

    var AUTO_HIDE_MS = 5000;          // 5 second baad hide
    var tvMode = false;
    var hideTimer = null;             // back button ka timer
    var forceHideTimer = null;        // controls ka force-hide timer
    var backBtn = null;
    var UI_IDS = ['controls', 'videoTitle', 'centerPlayBtn', 'closePlayerBtn'];

    /* ---------- Android TV detect karo ---------- */
    function markTvMode() { tvMode = true; updateVisibility(); }
    if (window.__androidTvNavigationInstalled) {
        markTvMode();
    } else {
        var poll = setInterval(function () {
            if (window.__androidTvNavigationInstalled) {
                clearInterval(poll);
                markTvMode();
            }
        }, 500);
        setTimeout(function () { clearInterval(poll); }, 15000);
    }

    function isPlayerOpen() {
        var modal = document.getElementById('playerModal');
        return !!(modal && modal.classList.contains('show'));
    }

    function areControlsVisible() {
        var controls = document.getElementById('controls');
        return !!(controls && !controls.classList.contains('ui-hidden'));
    }

    function isVideoPlaying() {
        var video = document.getElementById('video');
        return !!(video && !video.paused && !video.ended);
    }

    /* ---------- Back button banayo ---------- */
    function createButton() {
        var wrapper = document.getElementById('wrapper');
        if (!wrapper || backBtn) return;

        backBtn = document.createElement('button');
        backBtn.id = 'tvBackBtn';
        backBtn.setAttribute('title', 'Back');
        backBtn.innerHTML = '<i class="fas fa-arrow-left"></i>';

        backBtn.style.cssText =
            'position:absolute;' +
            'top:10px;' +
            'left:10px;' +
            'z-index:2000;' +
            'width:30px;' +
            'height:30px;' +
            'font-size:12px;' +
            'border-radius:50%;' +
            'background:rgba(0,0,0,0.5);' +
            'border:1px solid rgba(255,255,255,0.3);' +
            'color:#fff;' +
            'cursor:pointer;' +
            'display:flex;' +
            'align-items:center;' +
            'justify-content:center;' +
            'transition:all 0.2s ease;' +
            'opacity:0;' +
            'pointer-events:none;';

        backBtn.onclick = function (e) {
            e.preventDefault();
            e.stopPropagation();

            if (areControlsVisible()) {
                // Pehla back press: controls hide karo
                hideAllUI();
            } else {
                // Doosra back press: player band karo
                if (typeof window.closePlayer === 'function') {
                    window.closePlayer();
                }
            }
        };

        wrapper.appendChild(backBtn);
    }

    /* ---------- UI hide karna (shared) ---------- */
    function hideAllUI() {
        clearTimeout(forceHideTimer);
        UI_IDS.forEach(function (id) {
            var el = document.getElementById(id);
            if (el) el.classList.add('ui-hidden');
        });
        var wrapper = document.getElementById('wrapper');
        if (wrapper) wrapper.style.cursor = 'none';
        hideBackBtn();
    }

    /* ---------- Back button show/hide ---------- */
    function showBackBtn() {
        if (!backBtn) createButton();
        if (!backBtn) return;
        backBtn.style.opacity = '1';
        backBtn.style.pointerEvents = 'auto';

        clearTimeout(hideTimer);
        hideTimer = setTimeout(hideBackBtn, AUTO_HIDE_MS);
    }

    function hideBackBtn() {
        clearTimeout(hideTimer);
        if (!backBtn) return;
        backBtn.style.opacity = '0';
        backBtn.style.pointerEvents = 'none';
    }

    /* ---------- CONTROLS ka force-hide (TV fix) ----------
     * curl ka timer sirf ek baar isHoveringControls check karta hai.
     * TV par focus/mouseenter stuck ho jata hai -> controls kabhi hide
     * nahi hote. Ye timer 5s baad force-hide karta hai.
     * Video paused ho to hide NAHI karta (controls dekhne ka time).
     * Koi popup menu open ho to 2s baad dobara try karta hai.
     * ------------------------------------------------------- */
    function scheduleForceHide() {
        clearTimeout(forceHideTimer);

        if (!isVideoPlaying()) return;   // paused = controls visible rehne do

        forceHideTimer = setTimeout(function () {
            if (!isPlayerOpen()) return;

            // speed/quality jaise menu open hain? thodi der baad retry
            if (document.querySelector('.popup-menu.active')) {
                scheduleForceHide();
                return;
            }

            if (areControlsVisible()) {
                hideAllUI();   // force hide — hovering check NAHI
            }
        }, AUTO_HIDE_MS);
    }

    /* ---------- Main visibility logic ---------- */
    function updateVisibility() {
        if (!tvMode) return;
        if (isPlayerOpen() && areControlsVisible()) {
            showBackBtn();
            scheduleForceHide();   // controls ke liye bhi 5s timer
        } else {
            clearTimeout(forceHideTimer);
            hideBackBtn();
        }
    }

    /* ---------- Observers ---------- */
    function watchElements() {
        createButton();
        updateVisibility();

        var controls = document.getElementById('controls');
        var modal = document.getElementById('playerModal');
        var observer = new MutationObserver(updateVisibility);

        if (controls) observer.observe(controls, { attributes: true, attributeFilter: ['class'] });
        if (modal) observer.observe(modal, { attributes: true, attributeFilter: ['class'] });
    }

    // Remote ki koi bhi key dabane par dono timers reset ho jayein
    document.addEventListener('keydown', function () {
        if (!tvMode || !isPlayerOpen()) return;
        if (areControlsVisible()) {
            showBackBtn();
            scheduleForceHide();   // activity = 5s aur time
        }
    }, true);

    // Video pause/play hone par bhi timer sync karo
    document.addEventListener('play', function (e) {
        if (tvMode && e.target && e.target.id === 'video' && areControlsVisible()) {
            scheduleForceHide();
        }
    }, true);

    document.addEventListener('pause', function (e) {
        if (e.target && e.target.id === 'video') {
            clearTimeout(forceHideTimer);   // paused = hide mat karo
        }
    }, true);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', watchElements);
    } else {
        watchElements();
    }
})();
