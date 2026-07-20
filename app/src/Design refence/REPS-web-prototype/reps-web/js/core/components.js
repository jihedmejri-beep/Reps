/* ==========================================================================
   REPS — core/components.js
   HTML-string renderers for every shared component, so every screen that
   uses (say) WeightWidget or MotivationQuoteCard renders the *same* markup
   as Home — reuse, not recreation, per the brief for the Progress screen.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.components = (function () {
  const { formatWeight, unitLabel, formatInt } = REPS.utils;

  const icon = (name, cls = '') => `<svg class="${cls}"><use href="#${name}"/></svg>`;

  function sectionHeader(eyebrow, title) {
    return `
      <div class="section-header">
        <p class="section-header__eyebrow text-eyebrow">${eyebrow}</p>
        <h1 class="section-header__title text-section-title">${title}</h1>
      </div>`;
  }

  function metaChip(iconName, text) {
    return `<div class="meta-chip">${icon(iconName)}<span class="text-label-md">${text}</span></div>`;
  }

  /* ---- TodayWorkoutCard / RestDayCard --------------------------------- */
  function todayWorkoutCard(workout, muscleGroupsText) {
    return `
      <div class="today-card reveal" data-action="open-workout" data-ripple>
        <div class="today-card__top">
          <div style="min-width:0;">
            <p class="text-eyebrow c-green">Today · Workout</p>
            <h2 class="today-card__name text-section-title c-primary">${workout.name}</h2>
            <p class="today-card__muscles text-body-md">${muscleGroupsText}</p>
          </div>
          ${icon('icon-progress', 'today-card__glyph')}
        </div>
        <div class="meta-row">
          ${metaChip('icon-workouts', `${workout.exercises.length} exercises`)}
          ${metaChip('icon-schedule', `${workout.estimatedMinutes} min`)}
          ${metaChip('icon-progress', workout.difficulty)}
        </div>
        <button class="reps-btn reps-btn--primary" data-ripple data-action="start-workout" type="button">
          ${icon('icon-play', 'reps-btn__icon')}
          <span class="reps-btn__label text-button-label">Start Workout</span>
          <span class="reps-btn__spinner"></span>
        </button>
      </div>`;
  }

  function restDayCard() {
    return `
      <div class="rest-card reveal">
        <p class="text-eyebrow c-green">Today · Workout</p>
        <h2 class="rest-card__title text-section-title c-primary">Rest Day</h2>
        <p class="rest-card__subtext text-body-md">Nothing scheduled. Recovery is part of the plan.</p>
      </div>`;
  }

  /* ---- QuickActionsRow -------------------------------------------------- */
  function quickActionsRow(startEnabled) {
    const actions = [
      { key: 'start', icon: 'icon-play', label: 'Start', enabled: startEnabled },
      { key: 'weight', icon: 'icon-scale', label: 'Weight', enabled: true },
      { key: 'meal', icon: 'icon-nutrition', label: 'Meal', enabled: true },
      { key: 'timer', icon: 'icon-timer', label: 'Timer', enabled: true },
    ];
    return `
      <div class="quick-actions reveal">
        <p class="quick-actions__title text-title-sm">Quick Actions</p>
        <div class="quick-actions__row">
          ${actions.map(a => `
            <div class="quick-action ${a.enabled ? '' : 'is-disabled'}" data-action="quick-${a.key}">
              <button class="quick-action__tile" data-ripple type="button" ${a.enabled ? '' : 'disabled'} aria-label="${a.label}">
                ${icon(a.icon)}
              </button>
              <span class="quick-action__label text-label-sm">${a.label}</span>
            </div>`).join('')}
        </div>
      </div>`;
  }

  /* ---- WeightWidget ------------------------------------------------------ */
  function weightWidget(weightKg, deltaKg, units, { clickable = true } = {}) {
    if (weightKg == null) {
      return `
        <div class="weight-widget reveal" ${clickable ? 'data-action="open-weight" data-ripple' : ''}>
          <div class="weight-widget__label">${icon('icon-scale')}<span class="text-body-sm">Weight</span></div>
          <p class="weight-widget__empty text-body-md">No entries yet</p>
        </div>`;
    }
    let deltaHtml = '';
    if (deltaKg != null) {
      const gained = deltaKg > 0;
      const dirClass = gained ? 'is-up' : 'is-down';
      const dirIcon = gained ? 'icon-arrow-up' : 'icon-arrow-down';
      deltaHtml = `
        <div class="weight-widget__delta ${dirClass}">
          ${icon(dirIcon)}
          <span class="text-label-sm">${formatWeight(Math.abs(deltaKg), units)} ${unitLabel(units)} this week</span>
        </div>`;
    }
    return `
      <div class="weight-widget reveal" ${clickable ? 'data-action="open-weight" data-ripple' : ''}>
        <div class="weight-widget__label">${icon('icon-scale')}<span class="text-body-sm">Weight</span></div>
        <div class="weight-widget__value-row">
          <span class="text-stat-value c-primary">${formatWeight(weightKg, units)}</span>
          <span class="weight-widget__unit text-body-sm">${unitLabel(units)}</span>
        </div>
        ${deltaHtml}
      </div>`;
  }

  /* ---- StreakBadge + meter ------------------------------------------------ */
  function meterBars(filledCount, total = 7, heights = null) {
    const hs = heights || Array.from({ length: total }, (_, i) => 10 + i * 2);
    return `<div class="meter">${Array.from({ length: total }, (_, i) =>
      `<div class="meter__bar ${i < filledCount ? 'is-filled' : ''}" style="height:${hs[i]}px"></div>`).join('')}</div>`;
  }
  function streakBadge(streak) {
    const filled = Math.max(0, Math.min(7, streak.current));
    return `
      <div class="streak-badge reveal">
        ${icon('icon-flame', 'streak-badge__flame')}
        <div class="streak-badge__body">
          <p class="streak-badge__count text-title-md c-primary">${streak.current} day streak</p>
          <p class="streak-badge__nudge text-body-sm">Keep it alive — train today to hit ${streak.current + 1}.</p>
        </div>
        ${meterBars(filled)}
      </div>`;
  }

  /* ---- MotivationQuoteCard ------------------------------------------------ */
  function quoteCard(eyebrow, quote) {
    return `
      <div class="quote-card reveal">
        <span class="quote-card__mark">&rdquo;</span>
        <p class="quote-card__eyebrow text-eyebrow">${eyebrow}</p>
        <p class="quote-card__quote text-headline-sm">${quote.replace(/\n/g, '<br/>')}</p>
      </div>`;
  }

  /* ---- Stat card (Progress analytics grid) -------------------------------- */
  function statCard({ icon: iconName, label, value, unit = '', deltaText, deltaKind = 'flat' }) {
    const deltaIcon = deltaKind === 'up' ? 'icon-arrow-up' : deltaKind === 'down' ? 'icon-arrow-down' : null;
    return `
      <div class="stat-card reveal">
        <div class="stat-card__head text-label-md">${icon(iconName)}<span>${label}</span></div>
        <p class="stat-card__value text-stat-value c-primary">${value}${unit ? `<span class="unit text-body-sm">${unit}</span>` : ''}</p>
        ${deltaText ? `<div class="stat-card__delta is-${deltaKind} text-label-sm">${deltaIcon ? icon(deltaIcon) : ''}<span>${deltaText}</span></div>` : ''}
      </div>`;
  }

  /* ---- Chart card shell (head only — chart body mounted separately) ------ */
  function chartCardHead({ eyebrow, title, right = '' }) {
    return `
      <div class="chart-card__head">
        <div>
          <p class="chart-card__eyebrow text-eyebrow">${eyebrow}</p>
          <p class="chart-card__title text-title-md">${title}</p>
        </div>
        ${right}
      </div>`;
  }

  /* ---- PR card ------------------------------------------------------------ */
  function prCard(pr, exercise) {
    return `
      <div class="pr-card" data-ripple data-pr-id="${pr.id}">
        <div class="pr-card__tag text-label-sm">${icon('icon-bolt')}<span>${pr.tag.toUpperCase()}</span></div>
        <p class="pr-card__exercise text-title-sm truncate">${exercise.name}</p>
        <p class="pr-card__value text-headline-sm">${pr.weightKg}<span class="text-body-sm c-secondary"> kg</span> × ${pr.reps}</p>
        <p class="pr-card__delta text-label-sm">${pr.note ? pr.note : `+${pr.deltaKg}kg vs previous`}</p>
        <p class="pr-card__date text-label-sm">${REPS.utils.relativeDay(pr.date)}</p>
      </div>`;
  }

  /* ---- Achievement badge tile ---------------------------------------------- */
  function badgeTile(ach) {
    return `
      <div class="badge-tile ${ach.unlocked ? '' : 'is-locked'}" data-ripple data-ach-id="${ach.id}">
        <div class="badge-tile__icon">
          ${icon(ach.icon)}
          ${!ach.unlocked ? `<div class="badge-tile__lock">${icon('icon-lock')}</div>` : ''}
        </div>
        <p class="badge-tile__label text-label-sm">${ach.name}</p>
      </div>`;
  }

  /* ---- Timeline row (Recent Activity) -------------------------------------- */
  function timelineRow(session, isLast) {
    const prHtml = session.prsHit.length
      ? `<div class="timeline-row__pr text-label-sm">${icon('icon-bolt')}<span>PR · ${session.prsHit[0]}</span></div>` : '';
    return `
      <div class="timeline-row">
        <div class="timeline-row__rail">
          <div class="timeline-row__dot"></div>
          <div class="timeline-row__line"></div>
        </div>
        <div class="timeline-row__card">
          <div class="timeline-row__top">
            <span class="timeline-row__name text-title-sm truncate">${session.name}</span>
            <span class="timeline-row__date text-label-sm">${REPS.utils.relativeDay(session.date)}</span>
          </div>
          <div class="timeline-row__stats">
            <span class="timeline-row__stat text-body-sm">${icon('icon-schedule')}${session.durationMin} min</span>
            <span class="timeline-row__stat text-body-sm">${icon('icon-workouts')}${formatInt(session.volumeKg)} kg</span>
          </div>
          ${prHtml}
        </div>
      </div>`;
  }

  /* ---- Generic settings/list row ------------------------------------------- */
  function listRow({ icon: iconName, label, sub = '', value = '', chevron = true, danger = false, action = '' }) {
    return `
      <div class="list-row ${chevron ? 'is-tappable' : ''} ${danger ? 'is-danger' : ''}" data-ripple ${action ? `data-action="${action}"` : ''}>
        ${iconName ? `<div class="list-row__icon">${icon(iconName)}</div>` : ''}
        <div class="list-row__body">
          <p class="list-row__label text-body-lg">${label}</p>
          ${sub ? `<p class="list-row__sub text-body-sm">${sub}</p>` : ''}
        </div>
        ${value ? `<span class="list-row__value text-body-md">${value}</span>` : ''}
        ${chevron ? `<span class="list-row__chevron">${icon('icon-chevron-right')}</span>` : ''}
      </div>`;
  }

  function avatarInitial(name, sizeClass = '') {
    return `<div class="avatar ${sizeClass}"><span class="text-title-md">${(name || '?').trim().charAt(0).toUpperCase()}</span></div>`;
  }

  function skeletonCard(heightPx = 120) {
    return `<div class="surface-card skeleton skeleton-card" style="height:${heightPx}px"></div>`;
  }

  return {
    icon, sectionHeader, metaChip,
    todayWorkoutCard, restDayCard, quickActionsRow, weightWidget, streakBadge, meterBars, quoteCard,
    statCard, chartCardHead, prCard, badgeTile, timelineRow, listRow, avatarInitial, skeletonCard,
  };
})();
