/* =====================================================
   SETTINGS
   ===================================================== */

const AUTO_HIDE_MS = 5000;

let customHideTimer = null;


/* =====================================================
   CLEAR CONTROL TIMERS
   ===================================================== */

function clearControlTimers() {

    if (customHideTimer) {
        clearTimeout(customHideTimer);
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
   ALWAYS AUTO-HIDE AFTER 5 SEC
   PLAYING OR PAUSED
   ===================================================== */

window.showControls = function () {

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

    /*
     * IMPORTANT:
     * Clear EVERY previous hide timer first.
     */
    clearControlTimers();


    /* -----------------------------
       SHOW CONTROLS
       ----------------------------- */

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


    /* -----------------------------
       ALWAYS START 5 SEC TIMER
       ----------------------------- */

    customHideTimer = setTimeout(function () {

        customHideTimer = null;

        /*
         * Player closed meanwhile?
         */
        if (!isPlayerOpen()) {
            return;
        }

        /*
         * Popup open hai to thoda baad dobara check.
         */
        const popup =
            document.querySelector('.popup-menu.active');

        if (popup) {

            customHideTimer = setTimeout(function () {

                customHideTimer = null;

                if (isPlayerOpen()) {
                    hidePlayerControls();
                }

            }, 1000);

            return;
        }


        /*
         * IMPORTANT:
         * Video paused ho ya playing,
         * dono cases mein controls hide honge.
         */
        hidePlayerControls();

    }, AUTO_HIDE_MS);

};
