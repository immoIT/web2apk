(function () {
    'use strict';

    // =========================================================
    // CONFIG
    // =========================================================

    const HIDE_DELAY = 5000;
    const SWIPE_DISTANCE = 45;

    let hideTimer = null;
    let touchStartX = 0;
    let touchStartY = 0;

    // =========================================================
    // FIND PLAYER
    // Change these selectors if your Web2APK player uses
    // different IDs/classes.
    // =========================================================

    const player =
        document.querySelector('#playerModal') ||
        document.querySelector('.player-modal') ||
        document.querySelector('.video-player') ||
        document.querySelector('video')?.parentElement;

    if (!player) {
        console.warn('[PlayerControls] Player not found');
        return;
    }

    // =========================================================
    // CONTROL SELECTORS
    // Add your custom button classes here if needed.
    // =========================================================

    const CONTROL_SELECTOR = [
        'button',
        '[role="button"]',
        '.player-control',
        '.video-control',
        '.control-btn',
        '.play-btn',
        '.pause-btn',
        '.next-btn',
        '.prev-btn',
        '.fullscreen-btn'
    ].join(',');

    // =========================================================
    // GET CONTROLS
    // =========================================================

    function getControls() {
        return [...player.querySelectorAll(CONTROL_SELECTOR)]
            .filter(el => {
                const style = getComputedStyle(el);

                return (
                    !el.disabled &&
                    style.display !== 'none' &&
                    style.visibility !== 'hidden'
                );
            });
    }

    // =========================================================
    // SHOW CONTROLS
    // =========================================================

    function showControls() {

        player.classList.remove('controls-hidden');

        // Compatible with existing ui-hidden systems
        player.querySelectorAll(
            '.controls, .player-controls, .video-controls'
        ).forEach(el => {
            el.classList.remove('ui-hidden');
        });

        clearTimeout(hideTimer);

        hideTimer = setTimeout(() => {
            hideControls();
        }, HIDE_DELAY);
    }

    // =========================================================
    // HIDE CONTROLS
    // =========================================================

    function hideControls() {

        // Don't hide if a text/input element has focus
        const active = document.activeElement;

        if (
            active &&
            player.contains(active) &&
            (
                active.tagName === 'INPUT' ||
                active.tagName === 'TEXTAREA' ||
                active.tagName === 'SELECT'
            )
        ) {
            showControls();
            return;
        }

        player.classList.add('controls-hidden');

        player.querySelectorAll(
            '.controls, .player-controls, .video-controls'
        ).forEach(el => {
            el.classList.add('ui-hidden');
        });
    }

    // =========================================================
    // RESET HIDE TIMER
    // =========================================================

    function keepControlsAlive() {
        showControls();
    }

    // =========================================================
    // FOCUS FIRST CONTROL
    // =========================================================

    function focusFirstControl() {

        const controls = getControls();

        if (!controls.length) return;

        controls.forEach(el => {
            if (!el.hasAttribute('tabindex')) {
                el.setAttribute('tabindex', '0');
            }
        });

        const current = document.activeElement;

        if (!player.contains(current) || !controls.includes(current)) {
            controls[0].focus();
        }
    }

    // =========================================================
    // GET CURRENT FOCUS INDEX
    // =========================================================

    function getFocusedIndex(controls) {

        const current = document.activeElement;

        const index = controls.indexOf(current);

        return index >= 0 ? index : 0;
    }

    // =========================================================
    // D-PAD NAVIGATION
    //
    // Simple row-based navigation:
    //
    // LEFT  = previous
    // RIGHT = next
    // UP    = previous
    // DOWN  = next
    //
    // This works reliably with Web2APK/WebView controls.
    // =========================================================

    function navigate(direction) {

        const controls = getControls();

        if (!controls.length) return;

        let index = getFocusedIndex(controls);

        switch (direction) {

            case 'left':
            case 'up':
                index--;
                break;

            case 'right':
            case 'down':
                index++;
                break;
        }

        if (index < 0) {
            index = controls.length - 1;
        }

        if (index >= controls.length) {
            index = 0;
        }

        controls[index].focus();

        showControls();
    }

    // =========================================================
    // KEYBOARD / ANDROID TV D-PAD
    // =========================================================

    document.addEventListener('keydown', function (e) {

        // Player must be visible
        if (!player || !document.body.contains(player)) {
            return;
        }

        const style = getComputedStyle(player);

        if (
            style.display === 'none' ||
            style.visibility === 'hidden'
        ) {
            return;
        }

        switch (e.key) {

            // -----------------------------------------------
            // DPAD UP
            // -----------------------------------------------

            case 'ArrowUp':
                e.preventDefault();
                e.stopPropagation();

                if (player.classList.contains('controls-hidden')) {
                    showControls();
                    focusFirstControl();
                } else {
                    navigate('up');
                }

                break;


            // -----------------------------------------------
            // DPAD DOWN
            // -----------------------------------------------

            case 'ArrowDown':
                e.preventDefault();
                e.stopPropagation();

                if (player.classList.contains('controls-hidden')) {
                    showControls();
                    focusFirstControl();
                } else {
                    navigate('down');
                }

                break;


            // -----------------------------------------------
            // DPAD LEFT
            // -----------------------------------------------

            case 'ArrowLeft':
                e.preventDefault();
                e.stopPropagation();

                if (player.classList.contains('controls-hidden')) {
                    showControls();
                    focusFirstControl();
                } else {
                    navigate('left');
                }

                break;


            // -----------------------------------------------
            // DPAD RIGHT
            // -----------------------------------------------

            case 'ArrowRight':
                e.preventDefault();
                e.stopPropagation();

                if (player.classList.contains('controls-hidden')) {
                    showControls();
                    focusFirstControl();
                } else {
                    navigate('right');
                }

                break;


            // -----------------------------------------------
            // ENTER / OK
            // -----------------------------------------------

            case 'Enter':
            case 'OK':
                e.preventDefault();
                e.stopPropagation();

                if (player.classList.contains('controls-hidden')) {

                    showControls();
                    focusFirstControl();

                } else {

                    const active = document.activeElement;

                    if (
                        active &&
                        player.contains(active) &&
                        (
                            active.matches('button') ||
                            active.getAttribute('role') === 'button'
                        )
                    ) {
                        active.click();
                    }

                    showControls();
                }

                break;


            // -----------------------------------------------
            // SPACE
            // -----------------------------------------------

            case ' ':
                e.preventDefault();

                showControls();

                const focused = document.activeElement;

                if (
                    focused &&
                    player.contains(focused) &&
                    focused.matches('button, [role="button"]')
                ) {
                    focused.click();
                }

                break;


            // -----------------------------------------------
            // BACK / ESCAPE
            // -----------------------------------------------

            case 'Escape':
            case 'Backspace':

                e.preventDefault();

                // Let existing player close handler run if present
                player.dispatchEvent(
                    new CustomEvent('web2apk-player-back')
                );

                break;
        }

    }, true);

    // =========================================================
    // TOUCH - SWIPE UP
    // =========================================================

    player.addEventListener('touchstart', function (e) {

        if (!e.touches || e.touches.length !== 1) return;

        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;

    }, { passive: true });


    player.addEventListener('touchend', function (e) {

        if (!e.changedTouches || e.changedTouches.length !== 1) {
            return;
        }

        const touch = e.changedTouches[0];

        const deltaX = touch.clientX - touchStartX;
        const deltaY = touch.clientY - touchStartY;

        // Only vertical gestures
        if (Math.abs(deltaY) <= Math.abs(deltaX)) {
            return;
        }

        // SWIPE UP
        if (deltaY < -SWIPE_DISTANCE) {

            e.preventDefault();

            showControls();
            focusFirstControl();
        }

    }, { passive: false });


    // =========================================================
    // TOUCH / CLICK ON CONTROL
    // =========================================================

    player.addEventListener('pointerdown', function (e) {

        const control = e.target.closest(CONTROL_SELECTOR);

        if (control) {
            showControls();
        }

    }, true);


    player.addEventListener('click', function (e) {

        const control = e.target.closest(CONTROL_SELECTOR);

        if (control) {
            showControls();
        }

    }, true);


    // =========================================================
    // MOUSE MOVE
    // =========================================================

    player.addEventListener('mousemove', function () {
        showControls();
    }, { passive: true });


    // =========================================================
    // DYNAMIC CONTROLS
    //
    // If your JS creates buttons AFTER player opens,
    // automatically make them focusable.
    // =========================================================

    const observer = new MutationObserver(function () {

        getControls().forEach(control => {

            if (!control.hasAttribute('tabindex')) {
                control.setAttribute('tabindex', '0');
            }

        });

    });

    observer.observe(player, {
        childList: true,
        subtree: true
    });


    // =========================================================
    // OPTIONAL: EXPOSE API
    // Other custom JS can call:
    //
    // window.Web2APKPlayerControls.show()
    // window.Web2APKPlayerControls.hide()
    // window.Web2APKPlayerControls.reset()
    // =========================================================

    window.Web2APKPlayerControls = {

        show: function () {
            showControls();
        },

        hide: function () {
            hideControls();
        },

        reset: function () {
            clearTimeout(hideTimer);
            showControls();
        },

        focus: function () {
            showControls();
            focusFirstControl();
        }

    };

})();
