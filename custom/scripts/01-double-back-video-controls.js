/* =========================================================
   PLAYER HISTORY TRAP + TV BACK BUTTON
   CUSTOM PLAYER.JS OVERRIDE

   BEHAVIOUR:
   ---------------------------------------------------------
   BACK / CLICK / REMOTE INTERACTION
        ↓
   SHOW CONTROLS
        ↓
   5 SECONDS
        ↓
   HIDE CONTROLS

   Works for BOTH:
   - Playing video
   - Paused video
   ========================================================= */

(function () {

    'use strict';


    /* =====================================================
       SETTINGS
       ===================================================== */

    const AUTO_HIDE_MS = 5000;

    const DOUBLE_BACK_MS = 800;

    const DUPLICATE_EVENT_MS = 250;


    /* =====================================================
       STATE
       ===================================================== */

    let lastBackTime = 0;

    let customHideTimer = null;


    /* =====================================================
       ELEMENT HELPERS
       ===================================================== */

    function getPlayerElements() {

        return {

            modal:
                document.getElementById('playerModal'),

            controls:
                document.getElementById('controls'),

            title:
                document.getElementById('videoTitle'),

            centerPlay:
                document.getElementById('centerPlayBtn'),

            close:
                document.getElementById('closePlayerBtn'),

            wrapper:
                document.getElementById('wrapper'),

            video:
                document.getElementById('video')

        };

    }


    /* =====================================================
       PLAYER OPEN CHECK
       ===================================================== */

    function isPlayerOpen() {

        const modal =
            document.getElementById('playerModal');

        return !!(
            modal &&
            modal.classList.contains('show')
        );

    }


    /* =====================================================
       CLEAR ALL CONTROL TIMERS
       ===================================================== */

    function clearControlTimers() {

        /*
         * Our timer
         */

        if (customHideTimer) {

            clearTimeout(
                customHideTimer
            );

            customHideTimer = null;

        }


        /*
         * player.js timer
         */

        try {

            if (
                typeof controlHideTimer !== 'undefined' &&
                controlHideTimer
            ) {

                clearTimeout(
                    controlHideTimer
                );

            }

        } catch (e) {

            /*
             * Ignore if variable does not exist.
             */

        }

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


        /*
         * Timer already completed.
         */

        customHideTimer = null;


        /* -----------------------------
           CONTROLS
           ----------------------------- */

        if (controls) {

            controls.classList.add(
                'ui-hidden'
            );

        }


        /* -----------------------------
           TITLE
           ----------------------------- */

        if (title) {

            title.classList.add(
                'ui-hidden'
            );

        }


        /* -----------------------------
           CENTER PLAY
           ----------------------------- */

        if (centerPlay) {

            centerPlay.classList.add(
                'ui-hidden'
            );

        }


        /* -----------------------------
           CLOSE BUTTON
           ----------------------------- */

        if (close) {

            close.classList.add(
                'ui-hidden'
            );

        }


        /* -----------------------------
           CURSOR
           ----------------------------- */

        if (wrapper) {

            wrapper.style.cursor = 'none';

        }

    }


    /* =====================================================
       SHOW PLAYER CONTROLS
       
       IMPORTANT:
       -----------------------------------------------------
       This ALWAYS starts a fresh 5 second timer.

       It does NOT care whether video is:
       - playing
       - paused
       - ended

       The controls will hide after 5 seconds.
       ===================================================== */

    window.showControls = function () {

        const {
            controls,
            title,
            centerPlay,
            close,
            wrapper
        } = getPlayerElements();


        /*
         * No controls element?
         */

        if (!controls) {

            return;

        }


        /*
         * Cancel previous timers.
         *
         * This is important because every new
         * interaction should start a NEW 5 sec timer.
         */

        clearControlTimers();


        /* =================================================
           SHOW
           ================================================= */

        controls.classList.remove(
            'ui-hidden'
        );


        if (title) {

            title.classList.remove(
                'ui-hidden'
            );

        }


        if (centerPlay) {

            centerPlay.classList.remove(
                'ui-hidden'
            );

        }


        if (close) {

            close.classList.remove(
                'ui-hidden'
            );

        }


        if (wrapper) {

            wrapper.style.cursor =
                'default';

        }


        /* =================================================
           ALWAYS AUTO-HIDE AFTER 5 SECONDS
           
           IMPORTANT:
           NO video.paused CHECK HERE.
           ================================================= */

        customHideTimer =
            setTimeout(function () {

                customHideTimer = null;


                /*
                 * Player closed meanwhile?
                 */

                if (!isPlayerOpen()) {

                    return;

                }


                /* -----------------------------------------
                   POPUP CHECK
                   ----------------------------------------- */

                const popup =
                    document.querySelector(
                        '.popup-menu.active'
                    );


                /*
                 * If popup is open, don't immediately
                 * hide controls.
                 *
                 * Check again after 1 second.
                 */

                if (popup) {

                    customHideTimer =
                        setTimeout(
                            function () {

                                customHideTimer =
                                    null;


                                if (
                                    !isPlayerOpen()
                                ) {

                                    return;

                                }


                                const currentPopup =
                                    document.querySelector(
                                        '.popup-menu.active'
                                    );


                                /*
                                 * Popup still open:
                                 * wait another second.
                                 */

                                if (currentPopup) {

                                    window.showControls();

                                    return;

                                }


                                /*
                                 * Popup closed:
                                 * hide controls.
                                 */

                                hidePlayerControls();

                            },
                            1000
                        );

                    return;

                }


                /* -----------------------------------------
                   NORMAL HIDE
                   ----------------------------------------- */

                hidePlayerControls();

            }, AUTO_HIDE_MS);

    };


    /* =====================================================
       HISTORY TRAP
       ===================================================== */

    function ensureHistoryTrap() {

        const modal =
            document.getElementById(
                'playerModal'
            );


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
        document.getElementById(
            'playerModal'
        );


    if (playerModal) {

        const observer =
            new MutationObserver(
                function () {

                    /*
                     * Player opened
                     */

                    if (
                        playerModal.classList.contains(
                            'show'
                        )
                    ) {

                        ensureHistoryTrap();

                    }


                    /*
                     * Player closed
                     */

                    else if (
                        location.hash === '#tv-trap'
                    ) {

                        clearControlTimers();

                        /*
                         * Remove trap from browser history.
                         */

                        history.back();

                    }

                }
            );


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

            e.key === 'BrowserBack' ||

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

        /*
         * Player not open.
         */

        if (!isPlayerOpen()) {

            return;

        }


        const now =
            Date.now();


        const timeDiff =
            now - lastBackTime;


        /* =================================================
           DUPLICATE EVENT PROTECTION
           ================================================= */

        if (
            timeDiff < DUPLICATE_EVENT_MS
        ) {

            return;

        }


        /* =================================================
           SECOND BACK
           
           Within 800ms:
           CLOSE PLAYER
           ================================================= */

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
                typeof closePlayer ===
                'function'
            ) {

                closePlayer();

            }


            return;

        }


        /*
         * First BACK timestamp.
         */

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
             * Application rotation handler.
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


            /*
             * Show controls after returning
             * from landscape.
             */

            window.showControls();


            return;

        }


        /* =================================================
           CONTROLS HIDDEN
           
           First BACK:
           SHOW CONTROLS
           
           Then auto-hide after 5 sec.
           ================================================= */

        if (controlsHidden) {

            window.showControls();


            ensureHistoryTrap();

            return;

        }


        /* =================================================
           CONTROLS VISIBLE
           
           First BACK:
           HIDE CONTROLS
           ================================================= */

        /*
         * We intentionally don't require
         * video.playing here.
         *
         * This makes BACK work the same for
         * paused and playing video.
         */

        hidePlayerControls();


        /*
         * Optional toast.
         */

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

    }


    /* =====================================================
       TV / REMOTE BACK KEY HANDLER
       
       CAPTURE PHASE
       ===================================================== */

    window.addEventListener(
        'keydown',
        function (e) {

            /*
             * Ignore synthetic events.
             */

            if (!e.isTrusted) {

                return;

            }


            /*
             * Not BACK.
             */

            if (!isBackKey(e)) {

                return;

            }


            /*
             * Player not open.
             */

            if (!isPlayerOpen()) {

                return;

            }


            /*
             * IMPORTANT:
             *
             * Stop player.js from handling the
             * same BACK event.
             */

            e.preventDefault();

            e.stopPropagation();

            e.stopImmediatePropagation();


            handlePlayerBackAction(e);

        },
        true
    );


    /* =====================================================
       GENERAL REMOTE INTERACTION
       
       Any non-BACK keyboard interaction can show
       controls and restart the 5 sec timer.
       ===================================================== */

    window.addEventListener(
        'keydown',
        function (e) {

            if (!e.isTrusted) {

                return;

            }


            if (!isPlayerOpen()) {

                return;

            }


            /*
             * BACK is handled separately above.
             */

            if (isBackKey(e)) {

                return;

            }


            /*
             * Any other remote/key interaction:
             *
             * SHOW → 5 SEC → HIDE
             */

            window.showControls();

        },
        false
    );


    /* =====================================================
       MOUSE / TOUCH INTERACTION
       
       Click / touch:
       SHOW → 5 SEC → HIDE
       ===================================================== */

    document.addEventListener(
        'click',
        function (e) {

            if (!isPlayerOpen()) {

                return;

            }


            /*
             * Ignore clicks on the controls themselves
             * if you don't want them to restart the timer.
             *
             * Currently we DO restart the timer.
             */

            window.showControls();

        },
        false
    );


    document.addEventListener(
        'touchstart',
        function (e) {

            if (!isPlayerOpen()) {

                return;

            }


            window.showControls();

        },
        {
            passive: true,
            capture: false
        }
    );


    /* =====================================================
       MOUSE MOVE
       
       IMPORTANT:
       -----------------------------------------------------
       We do NOT dispatch synthetic mousemove events.

       Real mouse movement shows controls and restarts
       the 5 second timer.
       ===================================================== */

    document.addEventListener(
        'mousemove',
        function (e) {

            if (!isPlayerOpen()) {

                return;

            }


            window.showControls();

        },
        false
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
       
       IMPORTANT:
       DO NOT clear hide timer here.
       ===================================================== */

    document.addEventListener(
        'play',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                /*
                 * Do NOT clear timer.
                 *
                 * Controls continue their existing
                 * 5 second auto-hide countdown.
                 */

            }

        },
        true
    );


    /* =====================================================
       VIDEO PAUSE
       
       IMPORTANT:
       DO NOT clear hide timer here.
       ===================================================== */

    document.addEventListener(
        'pause',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                /*
                 * Do NOT clear timer.
                 *
                 * Controls will still hide after
                 * 5 seconds even when video is paused.
                 */

            }

        },
        true
    );


    /* =====================================================
       VIDEO ENDED
       
       IMPORTANT:
       DO NOT clear hide timer here.
       ===================================================== */

    document.addEventListener(
        'ended',
        function (e) {

            if (
                e.target &&
                e.target.id === 'video'
            ) {

                /*
                 * Do NOT clear timer.
                 *
                 * Controls remain governed by the
                 * same 5 second auto-hide behaviour.
                 */

            }

        },
        true
    );


    /* =====================================================
       INITIAL PLAYER OPEN
       
       If player is already open when this script runs,
       create history trap.
       ===================================================== */

    if (isPlayerOpen()) {

        ensureHistoryTrap();

    }


    /* =====================================================
       IMPORTANT NOTES
       =====================================================

       1. showControls()
          ALWAYS starts a 5 second timer.

       2. video.paused is NOT checked.

       3. pause/play/ended events do NOT cancel timer.

       4. Any real:
          - click
          - touch
          - mouse movement
          - remote key
          interaction restarts the 5 sec timer.

       5. BACK:
          - hidden controls -> SHOW
          - visible controls -> HIDE
          - second BACK within 800ms -> CLOSE

       6. No synthetic mousemove is generated.
       ===================================================== */


})();
