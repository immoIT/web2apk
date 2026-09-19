/* =========================================================
 * TV Back Button for curl video player (web2apk Android TV)
 * ========================================================= */
(function () {
    'use strict';
    if (window.__tvBackButtonInstalled) return;
    window.__tvBackButtonInstalled = true;

    var AUTO_HIDE_MS = 5000;          // 5 second baad hide
    var tvMode = false;
    var hideTimer = null;
    var backBtn = null;

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
                ['controls', 'videoTitle', 'centerPlayBtn', 'closePlayerBtn'].forEach(function (id) {
                    var el = document.getElementById(id);
                    if (el) el.classList.add('ui-hidden');
                });
                hideBackBtn();
            } else {
                // Doosra back press: player band karo
                if (typeof window.closePlayer === 'function') {
                    window.closePlayer();
                }
            }
        };

        wrapper.appendChild(backBtn);
    }

    /* ---------- Show / Hide with 5 second timer ---------- */
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

    function updateVisibility() {
        if (!tvMode) return;
        if (isPlayerOpen() && areControlsVisible()) {
            showBackBtn();
        } else {
            hideBackBtn();
        }
    }

    /* ---------- Observers: controls / modal ke class changes ---------- */
    function watchElements() {
        createButton();
        updateVisibility();

        var controls = document.getElementById('controls');
        var modal = document.getElementById('playerModal');
        var observer = new MutationObserver(updateVisibility);

        if (controls) observer.observe(controls, { attributes: true, attributeFilter: ['class'] });
        if (modal) observer.observe(modal, { attributes: true, attributeFilter: ['class'] });
    }

    // Remote ki koi bhi key dabane par 5 sec timer reset ho jaye
    document.addEventListener('keydown', function () {
        if (tvMode && isPlayerOpen() && areControlsVisible()) {
            showBackBtn();
        }
    }, true);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', watchElements);
    } else {
        watchElements();
    }
})();
