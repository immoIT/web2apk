/* =========================================================
 * TV Back Button for curl video player (web2apk Android TV)  v3
 * ------------------------------------------------------------
 * - Controls show hote hi top-left me Back button aata hai
 * - 5 second baad button + controls DONO force-hide ho jate
 *   hain (TV par mouseenter/focus stuck hone se bachne ke liye)
 * - D-PAD (arrow/OK) keys se hidden controls wapas show hote hain
 * - Remote ki koi bhi key dabane par 5s timer reset hota hai
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

    /* ---------- D-PAD SUPPORT (TV fix) ----------
     * curl player me arrow/OK keys ka handler nahi hai. TV par controls
     * hidden hone ke baad D-pad se wapas lane ka koi tareeka nahi.
     * Ab: player open ho + controls hidden hon + D-pad key aaye
     *     -> controls show (aur normal 5s timer reset)
     * ------------------------------------------- */
    var DPAD_KEYS = {
        13: true,                               // OK / Enter
        37: true, 38: true, 39: true, 40: true  // arrows
    };

    function revealControls() {
        if (typeof window.showControls === 'function') {
            window.showControls(AUTO_HIDE_MS);
        } else {
            // fallback: synthetic mousemove se curl ka listener trigger karo
            var wrapper = document.getElementById('wrapper');
            if (wrapper) {
                wrapper.dispatchEvent(new MouseEvent('mousemove', { bubbles: true }));
            }
        }
    }

    document.addEventListener('keydown', function (e) {
        if (!tvMode || !isPlayerOpen()) return;

        var kc = e.keyCode || e.which;
        var isDpad = DPAD_KEYS[kc] === true ||
                     (e.key && e.key.indexOf('Arrow') === 0) ||
                     e.key === 'Enter';

        if (!areControlsVisible()) {
            // Controls chhupe hain -> D-pad se dikhao
            if (isDpad) {
                e.preventDefault();      // page scroll/focus jump roko
                e.stopPropagation();
                revealControls();        // curl ka showControls(5000)
                // observer khud button+timer sambhal lega
            }
            return;
        }

        // Controls visible hain -> koi bhi key = 5s aur time
        showBackBtn();
        scheduleForceHide();
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
