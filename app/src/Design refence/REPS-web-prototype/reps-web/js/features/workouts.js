/* ==========================================================================
   REPS — features/workouts.js
   Workouts was a placeholder in RepsNavHost.kt; built new here using
   strings already defined for it (workouts_eyebrow/workouts_title/
   workouts_search/workouts_description/workouts_common_mistakes) and the
   same card/chip/list vocabulary as everywhere else.
   ========================================================================== */
(function () {
  const D = REPS.data;
  const C = REPS.components;

  const MUSCLE_GROUPS = ['All', 'Chest', 'Back', 'Legs', 'Shoulders', 'Arms', 'Abs', 'Glutes', 'Cardio'];
  let activeFilter = 'All';
  let query = '';

  function renderMyWorkouts(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:10px;">My Workouts</p>
      <div class="my-workouts-row no-scrollbar" id="my-workouts-row"></div>
    `;
    const row = root.querySelector('#my-workouts-row');
    row.innerHTML = D.workouts.map(w => `
      <div class="mini-workout-card" data-ripple data-workout-id="${w.id}">
        <p class="mini-workout-card__eyebrow text-eyebrow">${w.difficulty}</p>
        <p class="mini-workout-card__name text-title-sm">${w.name}</p>
        <p class="mini-workout-card__meta text-label-sm">${w.exercises.length} exercises · ${w.estimatedMinutes} min</p>
      </div>`).join('');
    row.querySelectorAll('.mini-workout-card').forEach((card) => {
      card.addEventListener('click', () => REPS.session.start(card.dataset.workoutId));
    });
  }

  function renderFilters(root) {
    root.innerHTML = `
      <div class="field" style="margin-bottom:12px;">
        <div class="field__control" style="min-height:46px;">
          <span class="field__icon">${C.icon('icon-search')}</span>
          <input class="field__input" id="exercise-search" type="text" placeholder="Search exercises" />
        </div>
      </div>
      <div class="chip-row no-scrollbar" id="muscle-chip-row"></div>
    `;
    const chipRow = root.querySelector('#muscle-chip-row');
    chipRow.innerHTML = MUSCLE_GROUPS.map(g => `<button class="chip text-label-md ${g === activeFilter ? 'is-active' : ''}" data-group="${g}">${g}</button>`).join('');
    chipRow.querySelectorAll('.chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        activeFilter = chip.dataset.group;
        chipRow.querySelectorAll('.chip').forEach(c => c.classList.toggle('is-active', c === chip));
        renderList(document.getElementById('exercise-list'));
      });
    });
    root.querySelector('#exercise-search').addEventListener('input', (e) => {
      query = e.target.value.trim().toLowerCase();
      renderList(document.getElementById('exercise-list'));
    });
  }

  function filteredExercises() {
    return D.exercises.filter((ex) => {
      const matchesGroup = activeFilter === 'All' || ex.muscleGroup === activeFilter;
      const matchesQuery = !query || ex.name.toLowerCase().includes(query);
      return matchesGroup && matchesQuery;
    });
  }

  function renderList(root) {
    const list = filteredExercises();
    if (!list.length) {
      root.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">${C.icon('icon-search')}</div>
          <p class="empty-state__title text-title-sm">No matches</p>
          <p class="text-body-sm">Try a different search or filter.</p>
        </div>`;
      return;
    }
    root.innerHTML = list.map(ex => `
      <div class="exercise-row" data-ripple data-ex-id="${ex.id}">
        <div class="exercise-row__icon">${C.icon('icon-workouts')}</div>
        <div class="exercise-row__body">
          <p class="exercise-row__name text-body-lg truncate">${ex.name}</p>
          <p class="exercise-row__meta text-label-sm">${ex.muscleGroup} · ${ex.equipment}</p>
        </div>
        <span class="exercise-row__difficulty text-label-sm">${ex.difficulty}</span>
      </div>`).join('');
    root.querySelectorAll('.exercise-row').forEach((row) => {
      row.addEventListener('click', () => openExerciseSheet(row.dataset.exId));
    });
  }

  function openExerciseSheet(exId) {
    const ex = D.exerciseById[exId];
    REPS.sheets.open({
      title: ex.name,
      bodyHTML: `
        <div class="chip-row" style="padding:0 0 14px;">
          <span class="chip text-label-sm is-active" style="pointer-events:none;">${ex.muscleGroup}</span>
          <span class="chip text-label-sm" style="pointer-events:none;">${ex.equipment}</span>
          <span class="chip text-label-sm" style="pointer-events:none;">${ex.difficulty}</span>
        </div>
        <p class="text-eyebrow c-green" style="margin-bottom:6px;">Description</p>
        <p class="text-body-md c-secondary" style="line-height:1.5;">${ex.description}</p>
        <p class="text-eyebrow c-green" style="margin-top:18px;margin-bottom:8px;">Common mistakes</p>
        <div style="display:flex;flex-direction:column;gap:10px;">
          ${ex.mistakes.map(m => `
            <div style="display:flex;gap:8px;align-items:flex-start;">
              <span style="color:var(--reps-error);flex-shrink:0;line-height:1.5;">&bull;</span>
              <span class="text-body-sm c-secondary" style="line-height:1.5;">${m}</span>
            </div>`).join('')}
        </div>
      `,
    });
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="workouts"]');
    const scrollEl = screen.querySelector('#workouts-scroll');
    const myWorkoutsEl = screen.querySelector('#my-workouts');
    const filtersEl = screen.querySelector('#workouts-filters');
    const listEl = screen.querySelector('#exercise-list');

    renderMyWorkouts(myWorkoutsEl);
    renderFilters(filtersEl);
    renderList(listEl);

    REPS.nav.attachScrollHide('workouts', scrollEl);
    REPS.nav.onTabReselect('workouts', () => scrollEl.scrollTo({ top: 0, behavior: 'smooth' }));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
