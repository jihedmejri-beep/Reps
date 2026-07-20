/* ==========================================================================
   REPS — features/timer.js
   Ports the intent of the Timer strings already reserved in strings.xml
   (timer_start/pause/resume/cancel, timer_running, timer_done). Opened as a
   sheet from Home's Quick Actions -> Timer, which is how a rest timer is
   actually used mid-session rather than as a standalone destination.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.timer = (function () {
  const PRESETS = [60, 90, 180, 300]; // seconds: 1, 1.5, 3, 5 min
  let totalSeconds = 90;
  let remaining = totalSeconds;
  let raf = null;
  let lastTs = null;
  let running = false;

  function open() {
    totalSeconds = 90;
    remaining = totalSeconds;
    running = false;

    REPS.sheets.open({
      title: 'Rest Timer',
      bodyHTML: `
        <div class="timer-face">
          <div class="timer-ring-wrap">
            <svg viewBox="0 0 120 120">
              <circle class="timer-ring-track" cx="60" cy="60" r="52"/>
              <circle class="timer-ring-fill" id="timer-ring-fill" cx="60" cy="60" r="52" stroke-dasharray="326.7"/>
            </svg>
            <div class="timer-ring-center">
              <span class="timer-ring-center__value" id="timer-value">01:30</span>
              <span class="timer-ring-center__label text-label-sm" id="timer-status">Ready</span>
            </div>
          </div>
          <div class="timer-presets" id="timer-presets">
            ${PRESETS.map(s => `<button class="chip text-label-md" data-secs="${s}">${s < 60 ? `${s}s` : `${Math.round(s / 60)} min`}</button>`).join('')}
          </div>
          <div class="timer-actions">
            <button class="reps-btn reps-btn--outlined" data-ripple type="button" id="timer-cancel"><span class="reps-btn__label text-button-label">Cancel</span></button>
            <button class="reps-btn reps-btn--primary" data-ripple type="button" id="timer-toggle"><span class="reps-btn__label text-button-label">Start</span></button>
          </div>
        </div>
      `,
      onOpen(body) { wire(body); },
      onClose() { stop(); },
    });
  }

  function wire(body) {
    const valueEl = body.querySelector('#timer-value');
    const statusEl = body.querySelector('#timer-status');
    const fillEl = body.querySelector('#timer-ring-fill');
    const toggleBtn = body.querySelector('#timer-toggle');
    const cancelBtn = body.querySelector('#timer-cancel');
    const presetsEl = body.querySelector('#timer-presets');
    const CIRC = 326.7;

    function paint() {
      valueEl.textContent = REPS.utils.formatClock(remaining);
      const frac = totalSeconds ? remaining / totalSeconds : 0;
      fillEl.style.strokeDashoffset = `${CIRC * (1 - frac)}`;
    }
    paint();

    presetsEl.querySelectorAll('.chip').forEach((chip) => {
      chip.classList.toggle('is-active', Number(chip.dataset.secs) === totalSeconds);
      chip.addEventListener('click', () => {
        if (running) return;
        totalSeconds = Number(chip.dataset.secs);
        remaining = totalSeconds;
        presetsEl.querySelectorAll('.chip').forEach(c => c.classList.toggle('is-active', c === chip));
        paint();
      });
    });

    function tick(ts) {
      if (!running) return;
      if (lastTs === null) lastTs = ts;
      const dt = (ts - lastTs) / 1000;
      lastTs = ts;
      remaining = Math.max(0, remaining - dt);
      paint();
      if (remaining <= 0) {
        running = false;
        statusEl.textContent = 'Time\u2019s up';
        toggleBtn.querySelector('.reps-btn__label').textContent = 'Restart';
        REPS.sheets.toast('Time is up.');
        if (navigator.vibrate) navigator.vibrate([120, 60, 120]);
        return;
      }
      raf = requestAnimationFrame(tick);
    }

    function start() {
      if (remaining <= 0) remaining = totalSeconds;
      running = true;
      lastTs = null;
      statusEl.textContent = 'Running';
      toggleBtn.querySelector('.reps-btn__label').textContent = 'Pause';
      raf = requestAnimationFrame(tick);
    }
    function pause() {
      running = false;
      statusEl.textContent = 'Paused';
      toggleBtn.querySelector('.reps-btn__label').textContent = 'Resume';
      if (raf) cancelAnimationFrame(raf);
    }
    toggleBtn.addEventListener('click', () => { running ? pause() : start(); });
    cancelBtn.addEventListener('click', () => REPS.nav.back());
  }

  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = null;
    lastTs = null;
  }

  return { open };
})();
