/* =========================================================
   PLAYER HISTORY TRAP & BACK BUTTON LOGIC
   ========================================================= */

let lastBackTime = 0;
let isHovering = false;

function ensureHistoryTrap() {
    const playerModal = document.getElementById('playerModal');

    if (playerModal && playerModal.classList.contains('show')) {
        if (location.hash !== '#tv-trap') {
            history.pushState(
                { tvTrap: true },
                "",
                location.href.split('#')[0] + '#tv-trap'
            );
        }
    }
}

const pModal = document.getElementById('playerModal');

if (pModal) {
    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {

            if (mutation.target.classList.contains('show')) {
                ensureHistoryTrap();

                const wrapper = document.getElementById('wrapper');

                if (wrapper) {
                    wrapper.dispatchEvent(
                        new MouseEvent('mousemove', {
                            bubbles: true
                        })
                    );
                }

            } else if (location.hash === '#tv-trap') {
                history.back();
            }

        });
    });

    observer.observe(pModal, {
        attributes: true,
        attributeFilter: ['class']
    });
}


/* =========================================================
   BACK BUTTON ACTION
   ========================================================= */

function handlePlayerBackAction(e) {
    const now = Date.now();
    const timeDiff = now - lastBackTime;

    // Ignore extremely rapid duplicate events
    if (timeDiff < 250) {
        if (e.type === 'popstate') {
            ensureHistoryTrap();
        }
        return;
    }

    // Second BACK press within 800ms = close player
    if (timeDiff <= 800) {
        lastBackTime = now;

        const closeBtn = document.getElementById('closePlayerBtn');

        if (closeBtn) {
            closeBtn.click();
        }

        return;
    }

    lastBackTime = now;

    const controls = document.getElementById('controls');
    const wrapper = document.getElementById('wrapper');

    const isControlsHidden =
        controls && controls.classList.contains('ui-hidden');

    const isRotated =
        wrapper && wrapper.classList.contains('player-landscape');


    // If player is rotated/landscape, exit that mode first
    if (isRotated) {
        document.dispatchEvent(
            new KeyboardEvent('keydown', {
                key: 'Escape',
                code: 'Escape',
                keyCode: 27,
                bubbles: true
            })
        );

        ensureHistoryTrap();
        return;
    }


    // If controls are hidden, show them
    if (isControlsHidden) {
        if (wrapper) {
            wrapper.dispatchEvent(
                new MouseEvent('mousemove', {
                    bubbles: true,
                    cancelable: true
                })
            );
        }

        ensureHistoryTrap();
        return;
    }


    // Controls are visible
    if (!isControlsHidden) {

        if (!isHovering) {
            const video = document.getElementById('video');

            if (video && !video.paused) {

                const els = [
                    'controls',
                    'videoTitle',
                    'centerPlayBtn',
                    'closePlayerBtn'
                ];

                els.forEach(id => {
                    const el = document.getElementById(id);

                    if (el) {
                        el.classList.add('ui-hidden');
                    }
                });

                if (wrapper) {
                    wrapper.style.cursor = "none";
                }
            }
        }

        if (typeof showToast === 'function') {
            showToast(
                'Double-press BACK to exit video',
                'warning'
            );
        }

        ensureHistoryTrap();
        return;
    }
}


/* =========================================================
   TV / REMOTE BACK KEY HANDLER
   ========================================================= */

window.addEventListener("keydown", (e) => {

    if (!e.isTrusted) return;

    const isBackKey =
        e.keyCode === 27 ||
        e.keyCode === 461 ||
        e.keyCode === 10009 ||
        e.keyCode === 8 ||
        e.key === "Escape" ||
        e.key === "Back";

    if (isBackKey) {

        const playerModal =
            document.getElementById('playerModal');

        if (
            playerModal &&
            playerModal.classList.contains('show')
        ) {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();

            handlePlayerBackAction(e);
        }
    }

}, true);


/* =========================================================
   BROWSER / TV HISTORY BACK HANDLER
   ========================================================= */

window.addEventListener("popstate", (e) => {

    const playerModal =
        document.getElementById('playerModal');

    if (
        playerModal &&
        playerModal.classList.contains('show')
    ) {
        handlePlayerBackAction(e);
    }

});
