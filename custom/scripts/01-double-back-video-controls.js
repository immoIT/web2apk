/* =========================================================
   PLAYER HISTORY TRAP + TV BACK BUTTON
   CUSTOM PLAYER.JS OVERRIDE
   ========================================================= */

(function () {
    'use strict';

    /* =====================================================
       SETTINGS
       ===================================================== */

    const AUTO_HIDE_MS = 5000;
    const DOUBLE_BACK_MS = 800;
    const DUPLICATE_EVENT_MS = 250;

    let lastBackTime = 0;
    let customHideTimer = null;


    /* =====================================================
       ELEMENT HELPERS
       ===================================================== */

    function getPlayerElements() {

        return {
            modal: document.getElementById('playerModal'),
            controls: document.getElementById('controls'),
            title: document.getElementById('videoTitle'),
            centerPlay: document.getElementById('centerPlayBtn'),
            close: document.getElementById('closePlayerBtn'),
            wrapper: document.getElementById('wrapper'),
            video: document.getElementById('video')
        };

    }


    function isPlayerOpen() {

        const modal = document.getElementById('playerModal');

        return !!(
            modal &&
            modal.classList.contains('show')
        );

    }


    /* =====================================================
       CLEAR ALL CONTROL TIMERS
       ===================================================== */

    function clearControlTimers() {

        clearTimeout(customHideTimer);

        /*
         * player.js may have its own timer.
         */
        try {

            if (
                typeof controlHideTimer !== 'undefined' &&
                controlHideTimer
            ) {
                clearTimeout(controlHideTimer);
            }

        } catch (e) {}

    }


    /* =====================================================
       HIDE PLAYER CONTROLS
       ===================================================== */

    function hidePlayerControls() {

        const {
            controls,
            title,
            centerPlay,
            close,
            wrapper
        } = getPlayerElements();


        clearControlTimers();


        if (controls) {
            controls.classList.add('ui-hidden');
        }

        if (title) {
            title.classList.add('ui-hidden');
        }

        if (centerPlay) {
            centerPlay.classList.add('ui-hidden');
        }

        if (close) {
            close.classList.add('ui-hidden');
        }

        if (wrapper) {
            wrapper.style.cursor = 'none';
        }

    }


    /* =====================================================
       SHOW CONTROLS
       OVERRIDES player.js showControls()
       ===================================================== */

    window.showControls = function (delay) {

        const {
            controls,
            title,
            centerPlay,
            close,
            wrapper,
            video
        } = getPlayerElements();


        if (!controls) {
            return;
        }


        const hideDelay =
            typeof delay === 'number'
                ? delay
                : AUTO_HIDE_MS;


        clearControlTimers();


        /*
         * SHOW
         */

        controls.classList.remove('ui-hidden');


        if (title) {
            title.classList.remove('ui-hidden');
        }


        if (centerPlay) {
            centerPlay.classList.remove('ui-hidden');
        }


        if (close) {
            close.classList.remove('ui-hidden');
        }


        if (wrapper) {
            wrapper.style.cursor = 'default';
        }


        /*
         * IMPORTANT:
         *
         * Do NOT check isHoveringControls here.
         *
         * TV remote / Android TV mouse emulation can leave
         * that variable true and prevent controls from hiding.
         */


        if (
            video &&
            !video.paused &&
            !video.ended
        ) {

            customHideTimer = setTimeout(function () {

                if (!isPlayerOpen()) {
                    return;
                }


                /*
                 * If a popup menu is open, don't hide.
                 */

                const popup =
                    document.querySelector(
                        '.popup-menu.active'
                    );


                if (popup) {

                    /*
                     * Check again shortly.
                     */

                    customHideTimer = setTimeout(
                        function () {

                            if (
                                isPlayerOpen() &&
                                video &&
                                !video.paused &&
                                !video.ended
                            ) {
                                hidePlayerControls();
                            }

                        },
                        1000
                    );

                    return;
                }


                hidePlayerControls();

            }, hideDelay);

        }

    };


    /* =====================================================
       HISTORY TRAP
       ===================================================== */

    function ensureHistoryTrap() {

        const modal =
            document.getElementById('playerModal');


        if (
            modal &&
            modal.classList.contains('show') &&
            location.hash !== '#tv-trap'
        ) {

            history.pushState(
                {
                    tvTrap: true
                },
                '',
                location.href.split('#')[0] +
                '#tv-trap'
            );

        }

    }


    /* =====================================================
       PLAYER MODAL OBSERVER
       ===================================================== */

    const playerModal =
        document.getElementById('playerModal');


    if (playerModal) {

        const observer =
            new MutationObserver(function () {

                if (
                    playerModal.classList.contains('show')
                ) {

                    ensureHistoryTrap();

                }

                else if (
                    location.hash === '#tv-trap'
                ) {

                    /*
                     * Player closed.
                     */

                    clearControlTimers();

                    history.back();

                }

            });


        observer.observe(
            playerModal,
            {
                attributes: true,
                attributeFilter: ['class']
            }
        );

    }


    /* =====================================================
       BACK KEY DETECTION
       ===================================================== */

    function isBackKey(e) {

        return (
            e.key === 'Escape' ||
            e.key === 'Back' ||
            e.keyCode === 27 ||
            e.keyCode === 461 ||
            e.keyCode === 10009 ||
            e.keyCode === 8
        );

    }


    /* =====================================================
       BACK BUTTON ACTION
       ===================================================== */

    function handlePlayerBackAction(e) {

        if (!isPlayerOpen()) {
            return;
        }


        const now =
            Date.now();


        const timeDiff =
            now - lastBackTime;


        /*
         * Ignore duplicate Android / TV events.
         */

        if (
            timeDiff < DUPLICATE_EVENT_MS
        ) {

            return;

        }


        /*
         * SECOND BACK
         * Close player.
         */

        if (
            timeDiff <= DOUBLE_BACK_MS
        ) {

            lastBackTime = now;

            clearControlTimers();


            const closeBtn =
                document.getElementById(
                    'closePlayerBtn'
                );


            if (closeBtn) {

                closeBtn.click();

            }

            else if (
                typeof closePlayer === 'function'
            ) {

                closePlayer();

            }


            return;

        }


        lastBackTime = now;


        const {
            controls,
            wrapper,
            video
        } = getPlayerElements();


        const controlsHidden =
            controls &&
            controls.classList.contains(
                'ui-hidden'
            );


        const isLandscape =
            wrapper &&
            wrapper.classList.contains(
                'player-landscape'
            );


        /* =================================================
           LANDSCAPE
           ================================================= */

        if (isLandscape) {

            /*
             * Use application's rotation handler
             * if available.
             */

            if (
                typeof setRotationState ===
                'function'
            ) {

                setRotationState(false);

            }

            else {

                document.dispatchEvent(
                    new KeyboardEvent(
                        'keydown',
                        {
                            key: 'Escape',
                            code: 'Escape',
                            keyCode: 27,
                            bubbles: true
                        }
                    )
                );

            }


            ensureHistoryTrap();

            return;

        }


        /* =================================================
           CONTROLS HIDDEN
           BACK = SHOW CONTROLS
           ================================================= */

        if (controlsHidden) {

            window.showControls(
                AUTO_HIDE_MS
            );


            ensureHistoryTrap();

            return;

        }


        /* =================================================
           CONTROLS VISIBLE
           FIRST BACK = HIDE
           ================================================= */

        if (
            video &&
            !video.paused &&
            !video.ended
        ) {

            hidePlayerControls();


            if (
                typeof showToast ===
                'function'
            ) {

                showToast(
                    'Double-press BACK to exit video',
                    'warning'
                );

            }


            ensureHistoryTrap();

            return;

        }


        /* =================================================
           PAUSED VIDEO
           ================================================= */

        ensureHistoryTrap();

    }


    /* =====================================================
       TV / REMOTE BACK KEY HANDLER
       CAPTURE PHASE
       ===================================================== */

    window.addEventListener(
        'keydown',
        function (e) {

            if (!e.isTrusted) {
                return;
            }


            if (!isBackKey(e)) {
                return;
            }


            if (!isPlayerOpen()) {
                return;
            }


            /*
             * STOP player.js BACK handler.
             *
             * This prevents two different BACK handlers
             * from running at the same time.
             */

            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();


            handlePlayerBackAction(e);

        },
        true
    );


    /* =====================================================
       BROWSER / TV HISTORY BACK
       ===================================================== */

    window.addEventListener(
        'popstate',
        function (e) {

            if (!isPlayerOpen()) {
                return;
            }


            handlePlayerBackAction(e);

        }
    );


    /* =====================================================
       VIDEO PLAY
       ===================================================== */

    document.addEventListener(
        'play',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                clearControlTimers();

            }

        },
        true
    );


    /* =====================================================
       VIDEO PAUSE
       ===================================================== */

    document.addEventListener(
        'pause',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                clearControlTimers();

            }

        },
        true
    );


    /* =====================================================
       VIDEO ENDED
       ===================================================== */

    document.addEventListener(
        'ended',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                clearControlTimers();

            }

        },
        true
    );


    /* =====================================================
       REMOVE OLD SYNTHETIC MOUSEMOVE PROBLEM
       ===================================================== */

    /*
     * DO NOT DO THIS:
     *
     * wrapper.dispatchEvent(
     *     new MouseEvent('mousemove')
     * );
     *
     * It can repeatedly call player.js showControls()
     * and reset the auto-hide behaviour.
     */


})();
