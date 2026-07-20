/* ==========================================================================
   REPS — features/splash.js
   Ports feature/splash/SplashScreen.kt. Timings are the exact constants from
   the Kotlin file (measured from the studio's reference start-animation
   video): FADE_IN_MS=500, WORDMARK_FADE_MS=470, HOLD_UNTIL_MS=2400,
   FADE_OUT_MS=120, HINT_PULSE_MS=2000.
   ========================================================================== */
(function () {
  const HOLD_UNTIL_MS = 2400;
  const FADE_OUT_MS = 120;

  let initialized = false;
  let leaving = false;
  let holdTimer = null;

  function init() {
    if (initialized) return;
    initialized = true;

    const screen = document.querySelector('.screen[data-screen="splash"]');
    const barsHost = screen.querySelector('.splash__bars');
    const wordmark = screen.querySelector('.splash__wordmark');
    const tagline = screen.querySelector('.splash__tagline');
    const hint = screen.querySelector('.splash__hint');

    REPS.charts.mountBars(barsHost, { running: true, color: 'var(--reps-green)' });

    function resetVisualState() {
      wordmark.style.transition = 'none';
      tagline.style.transition = 'none';
      wordmark.style.opacity = '0';
      tagline.style.opacity = '0';
    }

    function leave() {
      if (leaving) return;
      leaving = true;
      clearTimeout(holdTimer);
      screen.style.transition = `opacity ${FADE_OUT_MS}ms linear`;
      screen.style.opacity = '0';
      setTimeout(() => {
        REPS.nav.resetTo('login');
        // Reset the raw visual state so a *later* return to splash starts
        // clean — but do NOT call playIntro() here. playIntro() arms the
        // auto-advance timer, and splash isn't the visible screen anymore;
        // onEnter('splash', playIntro) below is what re-triggers it, and
        // only fires when the user actually navigates back to splash.
        setTimeout(() => {
          screen.style.transition = '';
          screen.style.opacity = '';
          leaving = false;
          resetVisualState();
        }, 60);
      }, FADE_OUT_MS);
    }

    function playIntro() {
      resetVisualState();
      void wordmark.offsetWidth;
      requestAnimationFrame(() => {
        wordmark.style.transition = 'opacity 470ms linear';
        tagline.style.transition = 'opacity 500ms linear';
        wordmark.style.opacity = '1';
        tagline.style.opacity = '1';
      });
      clearTimeout(holdTimer);
      holdTimer = setTimeout(leave, HOLD_UNTIL_MS);
    }

    screen.addEventListener('click', leave);
    hint.classList.add('pulse');

    REPS.nav.onEnter('splash', playIntro);
    playIntro();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
