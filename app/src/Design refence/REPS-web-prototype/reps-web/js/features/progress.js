/* ==========================================================================
   REPS — features/progress.js
   New screen (Progress was a placeholder in RepsNavHost.kt). Built entirely
   from the existing token/component vocabulary — SectionHeader, the card
   idiom, StreakBadge's meter, MotivationQuoteCard verbatim — extended with
   a small set of new, on-brand pieces (stat card, chart card, PR card,
   achievement tile, timeline row) defined in core/components.js.
   ========================================================================== */
(function () {
  const D = REPS.data;
  const C = REPS.components;
  const U = REPS.utils;
  const { formatInt, formatWeight, relativeDay, clamp } = U;

  const MONDAY_FIRST_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  const MONTHLY_GOAL = 20;

  let period = 'thisMonth'; // 'thisMonth' | 'lastMonth'
  let rangeDays = 60; // weight chart window
  let selectedLift = 'bench-press';
  let chartsLoaded = false;
  let lineCtrls = [];

  /* ---------------------------------------------------------------- HERO */
  function renderHero(root) {
    const today = new Date();
    const todaysSession = D.workoutSessions.find(s => U.isSameDay(s.date, today));
    const plannedWorkout = D.workoutById[D.todayWorkoutId];
    const done = D.streak.thisWeek.filter(s => s === 'done').length;
    const total = D.streak.thisWeek.filter(s => s !== 'future').length;

    const ringPct = todaysSession ? 100 : 0;
    const CIRC = 251.2; // 2 * PI * 40, matches --hero-ring-fill r=40 below

    root.innerHTML = `
      <div class="surface-card hero-today reveal">
        <div class="hero-ring-wrap">
          <svg viewBox="0 0 96 96">
            <circle class="hero-ring-track" cx="48" cy="48" r="40"/>
            <circle class="hero-ring-fill" id="hero-ring-fill" cx="48" cy="48" r="40" stroke-dasharray="${CIRC}" stroke-dashoffset="${CIRC}"/>
          </svg>
          <div class="hero-ring-center">
            <span class="val text-headline-sm">${ringPct}%</span>
            <span class="lbl text-label-sm">Today</span>
          </div>
        </div>
        <div class="hero-today__body">
          <p class="hero-today__title text-title-md">${todaysSession ? `${todaysSession.name} logged` : (plannedWorkout ? `${plannedWorkout.name} planned` : 'Rest day')}</p>
          <p class="hero-today__sub text-body-sm">${todaysSession
            ? `${todaysSession.durationMin} min · ${formatInt(todaysSession.volumeKg)} kg lifted`
            : (plannedWorkout ? 'Not started yet — Home has the start button.' : 'Nothing scheduled. Recovery is part of the plan.')}</p>
        </div>
      </div>

      <div class="surface-card week-meter-card reveal">
        <div class="week-meter-head">
          <p class="text-title-sm c-primary">Weekly Completion</p>
          <p class="text-label-md c-secondary">${done}/${total} this week</p>
        </div>
        <div class="week-meter-row" id="week-meter-row"></div>
      </div>

      ${C.quoteCard('Keep Going', weeklyMotivation(done, total))}
    `;

    const rowEl = root.querySelector('#week-meter-row');
    const todayMondayFirst = (today.getDay() + 6) % 7;
    rowEl.innerHTML = D.streak.thisWeek.map((state, i) => `
      <div class="week-day ${i === todayMondayFirst ? 'is-today' : ''}">
        <div class="week-day__bar"><div class="week-day__fill ${state !== 'done' ? 'is-rest' : ''}" data-target="${state === 'done' ? 100 : state === 'rest' ? 28 : 10}"></div></div>
        <span class="week-day__label text-label-sm">${MONDAY_FIRST_LETTERS[i]}</span>
      </div>`).join('');

    // Animate rings/bars in after paint.
    requestAnimationFrame(() => {
      const fill = root.querySelector('#hero-ring-fill');
      if (fill) fill.style.strokeDashoffset = `${CIRC * (1 - ringPct / 100)}`;
      REPS.charts.staggerReveal(rowEl.querySelectorAll('.week-day__fill'), (el) => {
        el.style.height = `${el.dataset.target}%`;
      }, { delay: 55, startDelay: 120 });
    });

    root.querySelectorAll('.reveal').forEach((el, i) => { el.style.animationDelay = `${i * 70}ms`; });
  }

  function weeklyMotivation(done, total) {
    if (total === 0) return 'A fresh week.\nMake the first move count.';
    const pct = done / total;
    if (pct >= 1) return 'Every session this week.\nThat\u2019s the whole job, done.';
    if (pct >= 0.6) return `${done} of ${total} down.\nFinish what you started.`;
    return 'Slow start this week.\nOne set beats zero sets.';
  }

  /* ----------------------------------------------------------- ANALYTICS */
  function periodDelta(curr, prev) {
    if (!prev) return { text: '', kind: 'flat' };
    const diff = curr - prev;
    if (Math.abs(diff) < 0.001) return { text: 'Same as last month', kind: 'flat' };
    const pct = Math.round((diff / prev) * 100);
    const kind = diff > 0 ? 'up' : 'down';
    return { text: `${diff > 0 ? '+' : ''}${pct}% vs last month`, kind };
  }

  function renderAnalytics(root) {
    root.innerHTML = `
      <div class="analytics-grid-head">
        <p class="text-title-sm c-primary">Analytics</p>
        <div class="segmented" id="period-segmented" style="width:180px;">
          <div class="segmented__indicator" id="period-indicator"></div>
          <button class="segmented__opt text-label-md" data-period="thisMonth" type="button">This month</button>
          <button class="segmented__opt text-label-md" data-period="lastMonth" type="button">Last month</button>
        </div>
      </div>
      <div class="stat-grid" id="analytics-grid"></div>
      <button class="expand-toggle text-label-md" id="analytics-expand-toggle" type="button">
        <span>More stats</span><svg><use href="#icon-chevron-down"/></svg>
      </button>
      <div class="expand-panel" id="analytics-expand-panel">
        <div class="stat-grid" style="margin-top:10px;" id="analytics-grid-extra"></div>
      </div>
    `;

    const seg = root.querySelector('#period-segmented');
    const indicator = root.querySelector('#period-indicator');
    function positionIndicator(animate) {
      const active = seg.querySelector(`[data-period="${period}"]`);
      if (!active) return;
      indicator.style.transition = animate ? '' : 'none';
      indicator.style.width = `${active.offsetWidth}px`;
      indicator.style.transform = `translateX(${active.offsetLeft - 3}px)`;
      if (!animate) requestAnimationFrame(() => { indicator.style.transition = ''; });
    }
    seg.querySelectorAll('.segmented__opt').forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.period === period);
      btn.addEventListener('click', () => {
        if (btn.dataset.period === period) return;
        period = btn.dataset.period;
        seg.querySelectorAll('.segmented__opt').forEach(b => b.classList.toggle('is-active', b === btn));
        positionIndicator(true);
        fillGrids();
      });
    });
    requestAnimationFrame(() => positionIndicator(false));

    function fillGrids() {
      const curr = D.analytics[period];
      const prev = period === 'thisMonth' ? D.analytics.lastMonth : null;
      const hours = (curr.minutes / 60).toFixed(1);

      root.querySelector('#analytics-grid').innerHTML = [
        C.statCard({ icon: 'icon-workouts', label: 'Workouts', value: curr.workouts, ...deltaFor(curr.workouts, prev?.workouts) }),
        C.statCard({ icon: 'icon-schedule', label: 'Duration', value: hours, unit: 'h', ...deltaFor(curr.minutes, prev?.minutes) }),
        C.statCard({ icon: 'icon-progress', label: 'Volume lifted', value: formatInt(curr.volumeKg), unit: 'kg', ...deltaFor(curr.volumeKg, prev?.volumeKg) }),
        C.statCard({ icon: 'icon-flame', label: 'Calories', value: formatInt(curr.calories), unit: 'kcal', ...deltaFor(curr.calories, prev?.calories) }),
      ].join('');

      root.querySelector('#analytics-grid-extra').innerHTML = [
        C.statCard({ icon: 'icon-target', label: 'Avg / workout', value: formatInt(curr.volumeKg / curr.workouts), unit: 'kg' }),
        C.statCard({ icon: 'icon-schedule', label: 'Avg duration', value: Math.round(curr.minutes / curr.workouts), unit: 'min' }),
      ].join('');
    }
    function deltaFor(curr, prev) {
      const d = periodDelta(curr, prev);
      return { deltaText: d.text, deltaKind: d.kind };
    }
    fillGrids();

    const toggle = root.querySelector('#analytics-expand-toggle');
    const panel = root.querySelector('#analytics-expand-panel');
    toggle.addEventListener('click', () => {
      const open = panel.classList.toggle('is-open');
      toggle.classList.toggle('is-open', open);
      toggle.querySelector('span').textContent = open ? 'Fewer stats' : 'More stats';
    });
  }

  /* --------------------------------------------------------------- CHARTS */
  function skeletonCharts(root) {
    root.innerHTML = Array.from({ length: 4 }, () => `<div class="surface-card">${C.skeletonCard(170)}</div>`).join('');
  }

  function renderCharts(root, { animate = true } = {}) {
    root.innerHTML = `
      <div class="surface-card chart-card" id="chart-weight"></div>
      <div class="surface-card chart-card" id="chart-strength"></div>
      <div class="surface-card chart-card" id="chart-frequency"></div>
      <div class="surface-card chart-card" id="chart-muscle"></div>
    `;
    lineCtrls.forEach(c => c.destroy?.());
    lineCtrls = [];
    mountWeightChart(root.querySelector('#chart-weight'), animate);
    mountStrengthChart(root.querySelector('#chart-strength'), animate);
    mountFrequencyChart(root.querySelector('#chart-frequency'), animate);
    mountMuscleChart(root.querySelector('#chart-muscle'), animate);

    if (animate) {
      root.querySelectorAll('.chart-card').forEach((el, i) => {
        el.classList.add('reveal');
        el.style.animationDelay = `${i * 90}ms`;
      });
    }
  }

  function mountWeightChart(card, animate) {
    const entries = D.weightEntries.slice(-rangeDays);
    const latest = entries[entries.length - 1].weightKg;
    const first = entries[0].weightKg;
    const diff = Math.round((latest - first) * 10) / 10;
    card.innerHTML = `
      ${C.chartCardHead({
        eyebrow: 'Weight Progression', title: `${formatWeight(latest, D.user.units)} ${U.unitLabel(D.user.units)}`,
        right: `<div class="chip-row" id="weight-range-chips" style="padding:0;">
          <button class="chip text-label-sm" data-range="30">30D</button>
          <button class="chip text-label-sm" data-range="60">60D</button>
          <button class="chip text-label-sm" data-range="90">90D</button>
        </div>`,
      })}
      <p class="chart-card__caption text-label-sm ${diff <= 0 ? 'c-green' : ''}" style="margin-bottom:6px;">
        ${diff === 0 ? 'Holding steady' : `${diff > 0 ? '+' : ''}${diff} ${U.unitLabel(D.user.units)} over ${rangeDays} days`}
      </p>
      <div class="chart-card__canvas-wrap" id="weight-canvas"></div>
    `;
    card.querySelectorAll('#weight-range-chips .chip').forEach((chip) => {
      chip.classList.toggle('is-active', Number(chip.dataset.range) === rangeDays);
      chip.addEventListener('click', () => {
        rangeDays = Number(chip.dataset.range);
        mountWeightChart(card, true);
      });
    });
    const values = entries.map(e => U.displayWeight(e.weightKg, D.user.units));
    const ctrl = REPS.charts.renderLineChart(card.querySelector('#weight-canvas'), values, { animate });
    lineCtrls.push(ctrl);
  }

  function mountStrengthChart(card, animate) {
    const lifts = Object.entries(D.strengthSeries);
    const series = D.strengthSeries[selectedLift];
    card.innerHTML = `
      ${C.chartCardHead({ eyebrow: 'Strength Progression', title: series.label })}
      <div class="lift-picker no-scrollbar" id="lift-picker"></div>
      <div class="chart-card__canvas-wrap" id="strength-canvas"></div>
    `;
    const picker = card.querySelector('#lift-picker');
    picker.innerHTML = lifts.map(([id, s]) => `<button class="chip text-label-sm ${id === selectedLift ? 'is-active' : ''}" data-lift="${id}">${s.label}</button>`).join('');
    picker.querySelectorAll('.chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        selectedLift = chip.dataset.lift;
        mountStrengthChart(card, true);
      });
    });
    const values = series.points.map(p => U.displayWeight(p.weightKg, D.user.units));
    const ctrl = REPS.charts.renderLineChart(card.querySelector('#strength-canvas'), values, { animate, color: 'var(--reps-green)' });
    lineCtrls.push(ctrl);
  }

  function mountFrequencyChart(card, animate) {
    const max = Math.max(...D.weeklyFrequency.map(w => w.count));
    card.innerHTML = `
      ${C.chartCardHead({ eyebrow: 'Workout Frequency', title: 'Last 8 weeks' })}
      <div class="week-meter-row" style="margin-top:6px;" id="freq-row"></div>
    `;
    const row = card.querySelector('#freq-row');
    row.innerHTML = D.weeklyFrequency.map(w => `
      <div class="week-day">
        <div class="week-day__bar" style="height:44px;"><div class="week-day__fill" data-target="${(w.count / max) * 100}"></div></div>
        <span class="week-day__label text-label-sm">${w.label}</span>
      </div>`).join('');
    if (animate) {
      row.querySelectorAll('.week-day__fill').forEach(el => { el.style.height = '0%'; });
      REPS.charts.staggerReveal(row.querySelectorAll('.week-day__fill'), (el) => { el.style.height = `${el.dataset.target}%`; }, { delay: 60, startDelay: 100 });
    } else {
      row.querySelectorAll('.week-day__fill').forEach(el => { el.style.height = `${el.dataset.target}%`; });
    }
  }

  function mountMuscleChart(card, animate) {
    card.innerHTML = `
      ${C.chartCardHead({ eyebrow: 'Muscle Group Distribution', title: 'By training volume' })}
      <div class="rank-list" id="muscle-rank-list" style="margin-top:4px;"></div>
    `;
    const list = card.querySelector('#muscle-rank-list');
    list.innerHTML = D.muscleDistribution.map(m => `
      <div class="rank-row">
        <span class="rank-row__label text-label-md">${m.group}</span>
        <div class="rank-row__track"><div class="rank-row__fill" data-target="${m.pct}"></div></div>
        <span class="rank-row__pct text-label-md">${m.pct}%</span>
      </div>`).join('');
    if (animate) {
      REPS.charts.staggerReveal(list.querySelectorAll('.rank-row__fill'), (el) => { el.style.width = `${el.dataset.target}%`; }, { delay: 70, startDelay: 100 });
    } else {
      list.querySelectorAll('.rank-row__fill').forEach(el => { el.style.width = `${el.dataset.target}%`; });
    }
  }

  /* ---------------------------------------------------------- PRS / STREAK */
  function renderPRs(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:10px;">Personal Records</p>
      <div class="pr-row no-scrollbar" id="pr-row"></div>
    `;
    const row = root.querySelector('#pr-row');
    row.innerHTML = D.personalRecords.map(pr => C.prCard(pr, D.exerciseById[pr.exerciseId])).join('');
    row.querySelectorAll('.pr-card').forEach((cardEl) => {
      cardEl.addEventListener('click', () => openPrSheet(cardEl.dataset.prId));
    });
  }

  function openPrSheet(prId) {
    const pr = D.personalRecords.find(p => p.id === prId);
    const ex = D.exerciseById[pr.exerciseId];
    REPS.sheets.open({
      title: 'Personal Record',
      bodyHTML: `
        <div class="pr-card" style="width:100%;padding:0;">
          <div class="pr-card__tag text-label-sm">${C.icon('icon-bolt')}<span>${pr.tag.toUpperCase()}</span></div>
          <p class="text-headline-sm c-primary" style="margin-top:8px;">${ex.name}</p>
          <p class="text-stat-value c-primary" style="margin-top:10px;">${pr.weightKg}<span class="text-body-md c-secondary"> kg</span> × ${pr.reps}</p>
          <p class="text-body-sm c-secondary" style="margin-top:6px;">${pr.note ? pr.note : `Up ${pr.deltaKg} kg from your previous best${pr.deltaPct ? ` — a ${pr.deltaPct}% jump` : ''}.`}</p>
          <p class="text-label-sm c-tertiary" style="margin-top:14px;">Set ${relativeDay(pr.date)} · ${ex.muscleGroup} · ${ex.equipment}</p>
        </div>
        <button class="reps-btn reps-btn--outlined" style="margin-top:20px;" data-ripple type="button" id="pr-view-exercise">
          <span class="reps-btn__label text-button-label">View exercise</span>
        </button>
      `,
      onOpen(body) {
        body.querySelector('#pr-view-exercise').addEventListener('click', () => {
          REPS.sheets.toast('Opening exercise details…');
        });
      },
    });
  }

  function renderStreak(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:10px;">Streak</p>
      <div class="streak-stats">
        <div class="streak-stat">
          <div class="streak-stat__label text-label-sm">${C.icon('icon-flame')}<span>Current</span></div>
          <p class="streak-stat__value text-headline-sm">${D.streak.current}<span class="text-body-sm c-secondary"> days</span></p>
        </div>
        <div class="streak-stat">
          <div class="streak-stat__label text-label-sm">${C.icon('icon-trophy')}<span>Longest</span></div>
          <p class="streak-stat__value text-headline-sm">${D.streak.longest}<span class="text-body-sm c-secondary"> days</span></p>
        </div>
        <div class="streak-stat">
          <div class="streak-stat__label text-label-sm">${C.icon('icon-target')}<span>Consistency</span></div>
          <p class="streak-stat__value text-headline-sm">${Math.round(D.streak.weeklyConsistency * 100)}<span class="text-body-sm c-secondary">%</span></p>
        </div>
      </div>
    `;
  }

  /* ---------------------------------------------------------- ACHIEVEMENTS */
  function renderAchievements(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:10px;">Achievements</p>
      <div class="badge-row no-scrollbar" id="badge-row"></div>
    `;
    const row = root.querySelector('#badge-row');
    row.innerHTML = D.achievements.map(a => C.badgeTile(a)).join('');
    row.querySelectorAll('.badge-tile').forEach((tile) => {
      tile.addEventListener('click', () => openAchievementSheet(tile.dataset.achId));
    });
  }

  function openAchievementSheet(achId) {
    const a = D.achievements.find(x => x.id === achId);
    const progressPct = a.unlocked ? 100 : Math.round((a.progress || 0) * 100);
    REPS.sheets.open({
      title: a.unlocked ? 'Achievement Unlocked' : 'In Progress',
      bodyHTML: `
        <div style="display:flex;flex-direction:column;align-items:center;text-align:center;padding:6px 0 4px;">
          <div class="badge-tile__icon" style="width:84px;">${C.icon(a.icon)}</div>
          <p class="text-title-lg c-primary" style="margin-top:16px;">${a.name}</p>
          <p class="text-body-sm c-secondary" style="margin-top:6px;">
            ${a.unlocked ? `Earned ${relativeDay(a.date).toLowerCase()}.` : `${progressPct}% of the way there — keep going.`}
          </p>
          ${!a.unlocked ? `
            <div class="rank-row__track" style="width:100%;margin-top:16px;">
              <div class="rank-row__fill" style="width:${progressPct}%;"></div>
            </div>` : ''}
        </div>
      `,
    });
  }

  /* ------------------------------------------------------------- ACTIVITY */
  function renderActivity(root) {
    const sessions = D.workoutSessions;
    const avgPerWeek = (sessions.length / (16 / 7)).toFixed(1);
    const avgDuration = Math.round(sessions.reduce((s, x) => s + x.durationMin, 0) / sessions.length);

    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:10px;">Recent Activity</p>
      <div class="quick-stat-strip">
        <div class="quick-stat-chip"><p class="quick-stat-chip__value text-title-md">${avgPerWeek}</p><p class="quick-stat-chip__label text-label-sm">Per week</p></div>
        <div class="quick-stat-chip"><p class="quick-stat-chip__value text-title-md">${avgDuration}<span style="font-size:.7em;">min</span></p><p class="quick-stat-chip__label text-label-sm">Avg length</p></div>
        <div class="quick-stat-chip"><p class="quick-stat-chip__value text-title-md">${sessions.filter(s => s.prsHit.length).length}</p><p class="quick-stat-chip__label text-label-sm">PRs hit</p></div>
      </div>
      <div class="timeline" id="activity-timeline"></div>
    `;
    const tl = root.querySelector('#activity-timeline');
    tl.innerHTML = sessions.map((s, i) => C.timelineRow(s, i === sessions.length - 1)).join('');
  }

  /* --------------------------------------------------------- ADD WEIGHT */
  function openAddWeightSheet() {
    const units = D.user.units;
    let handle = null;
    handle = REPS.sheets.open({
      title: 'Add weight',
      bodyHTML: `
        <div class="field" id="aw-field">
          <label class="field__label text-eyebrow">Weight (${U.unitLabel(units)})</label>
          <div class="field__control">
            <span class="field__icon">${C.icon('icon-scale')}</span>
            <input class="field__input" id="aw-input" type="number" inputmode="decimal" step="0.1" placeholder="${U.formatWeight(D.currentWeightKg, units)}"/>
          </div>
          <p class="field__error text-body-sm"></p>
        </div>
        <div style="display:flex;gap:10px;margin-top:18px;">
          <button class="reps-btn reps-btn--outlined" data-ripple type="button" id="aw-cancel"><span class="reps-btn__label text-button-label">Cancel</span></button>
          <button class="reps-btn reps-btn--primary" data-ripple type="button" id="aw-save"><span class="reps-btn__label text-button-label">Save</span><span class="reps-btn__spinner"></span></button>
        </div>
      `,
      onOpen(body) {
        const input = body.querySelector('#aw-input');
        const fieldEl = body.querySelector('#aw-field');
        const errEl = fieldEl.querySelector('.field__error');
        setTimeout(() => input.focus(), 260);
        body.querySelector('#aw-cancel').addEventListener('click', () => handle.close());
        body.querySelector('#aw-save').addEventListener('click', () => {
          const raw = parseFloat(input.value);
          if (!input.value || Number.isNaN(raw) || raw <= 0) {
            fieldEl.classList.add('has-error');
            errEl.textContent = 'Enter a valid weight.';
            return;
          }
          const kg = Math.round(U.weightToKg(raw, units) * 10) / 10;
          D.weightEntries.push({ date: new Date(), weightKg: kg });
          D.currentWeightKg = kg;
          handle.close();
          REPS.sheets.toast('Weight logged.');
          const weightCard = document.querySelector('#chart-weight');
          if (weightCard) mountWeightChart(weightCard, true);
        });
      },
    });
  }

  /* ------------------------------------------------------------------ INIT */
  function init() {
    const screen = document.querySelector('.screen[data-screen="progress"]');
    const scrollEl = screen.querySelector('#progress-scroll');
    const ptrEl = screen.querySelector('[data-ptr]');

    const heroEl = screen.querySelector('#progress-hero');
    const analyticsEl = screen.querySelector('#progress-analytics');
    const chartsEl = screen.querySelector('#progress-charts');
    const prEl = screen.querySelector('#progress-prs');
    const streakEl = screen.querySelector('#progress-streak');
    const achEl = screen.querySelector('#progress-achievements');
    const activityEl = screen.querySelector('#progress-activity');

    function renderStaticParts() {
      renderHero(heroEl);
      renderAnalytics(analyticsEl);
      renderPRs(prEl);
      renderStreak(streakEl);
      renderAchievements(achEl);
      renderActivity(activityEl);
    }

    function fullLoad() {
      skeletonCharts(chartsEl);
      renderStaticParts();
      setTimeout(() => { renderCharts(chartsEl, { animate: true }); chartsLoaded = true; }, 650);
    }

    REPS.nav.onEnter('progress', () => {
      if (!chartsLoaded) fullLoad();
    });
    REPS.nav.onTabReselect('progress', () => scrollEl.scrollTo({ top: 0, behavior: 'smooth' }));

    REPS.gestures.initPullToRefresh(scrollEl, ptrEl, {
      onRefresh: () => new Promise((resolve) => {
        renderStaticParts();
        setTimeout(() => { renderCharts(chartsEl, { animate: true }); resolve(); }, 550);
      }),
    });
    REPS.nav.attachScrollHide('progress', scrollEl);

    // First activation may happen before onEnter fires if progress is the
    // very first screen shown (it never is here, but this keeps the module
    // self-sufficient if start-tab logic ever changes).
    if (REPS.nav.getActiveTab() === 'progress') fullLoad();

    REPS.progress = { openAddWeight: openAddWeightSheet };
  }

  document.addEventListener('DOMContentLoaded', init);
})();
