/* ==========================================================================
   REPS — features/session.js
   A live logging view for a WorkoutSession (domain/model/Workout.kt's
   WorkoutSession, distinct from the Workout template it came from): the
   same exercises/sets as the template, but each set can be checked off as
   completed with its own weight/reps, and "Finish workout" writes a new
   entry into workoutSessions — same shape Progress's timeline already reads.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.session = (function () {
  const D = REPS.data;
  const C = REPS.components;
  const U = REPS.utils;

  let activeWorkoutId = null;
  /** exerciseId -> array of { weightKg, reps, completed } (mutable working copy) */
  let working = {};

  function start(workoutId) {
    activeWorkoutId = workoutId;
    const workout = D.workoutById[workoutId];
    working = {};
    workout.exercises.forEach((we) => {
      working[we.exerciseId] = we.sets.map(s => ({ weightKg: s.weightKg, reps: s.reps, completed: false }));
    });
    REPS.nav.push('session');
    render();
  }

  function completedSetCount() {
    return Object.values(working).flat().filter(s => s.completed).length;
  }
  function totalSetCount() {
    return Object.values(working).flat().length;
  }
  function currentVolume() {
    return Object.values(working).flat().filter(s => s.completed).reduce((sum, s) => sum + s.weightKg * s.reps, 0);
  }

  function render() {
    const screen = document.querySelector('.screen[data-screen="session"]');
    const workout = D.workoutById[activeWorkoutId];
    if (!workout) return;

    screen.querySelector('#session-title').textContent = workout.name;

    const summaryEl = screen.querySelector('#session-summary');
    summaryEl.innerHTML = [
      C.statCard({ icon: 'icon-workouts', label: 'Exercises', value: workout.exercises.length }),
      C.statCard({ icon: 'icon-schedule', label: 'Est. time', value: workout.estimatedMinutes, unit: 'min' }),
      C.statCard({ icon: 'icon-progress', label: 'Volume so far', value: U.formatInt(currentVolume()), unit: 'kg' }),
    ].join('');

    const listEl = screen.querySelector('#session-exercise-list');
    listEl.innerHTML = workout.exercises.map(we => exerciseCardHTML(we)).join('');
    wireExerciseCards(listEl);

    updateFinishBar(screen);
  }

  function exerciseCardHTML(we) {
    const ex = D.exerciseById[we.exerciseId];
    const sets = working[we.exerciseId];
    return `
      <div class="session-exercise" data-ex-id="${we.exerciseId}">
        <div class="session-exercise__head">
          <div style="min-width:0;">
            <p class="session-exercise__name text-title-sm truncate">${ex.name}</p>
            <p class="session-exercise__muscle text-label-sm">${ex.muscleGroup} · ${ex.equipment}</p>
          </div>
        </div>
        <div class="set-table">
          <div class="set-table__labels">
            <span class="text-label-sm">Set</span>
            <span class="text-label-sm">${U.unitLabel(D.user.units)}</span>
            <span class="text-label-sm">Reps</span>
            <span></span>
          </div>
          ${sets.map((s, i) => setRowHTML(we.exerciseId, s, i)).join('')}
        </div>
        <button class="add-set-btn text-label-md" data-ripple type="button" data-add-set>
          ${C.icon('icon-plus')}<span>Add set</span>
        </button>
      </div>`;
  }

  function setRowHTML(exerciseId, s, i) {
    return `
      <div class="set-row" data-set-index="${i}">
        <span class="set-row__index text-label-md">${i + 1}</span>
        <input class="set-row__input" type="number" inputmode="decimal" data-field="weight" value="${U.displayWeight(s.weightKg, D.user.units)}"/>
        <input class="set-row__input" type="number" inputmode="numeric" data-field="reps" value="${s.reps}"/>
        <button class="set-row__check ${s.completed ? 'is-done' : ''}" data-ripple type="button" aria-label="Mark set complete">
          ${C.icon('icon-check')}
        </button>
      </div>`;
  }

  function wireExerciseCards(listEl) {
    listEl.querySelectorAll('.session-exercise').forEach((card) => {
      const exerciseId = card.dataset.exId;

      card.querySelectorAll('.set-row').forEach((row) => attachRowHandlers(card, row, exerciseId));

      card.querySelector('[data-add-set]').addEventListener('click', () => {
        const sets = working[exerciseId];
        const last = sets[sets.length - 1];
        sets.push({ weightKg: last ? last.weightKg : 20, reps: last ? last.reps : 10, completed: false });
        const table = card.querySelector('.set-table');
        const row = document.createElement('div');
        row.innerHTML = setRowHTML(exerciseId, sets[sets.length - 1], sets.length - 1);
        const newRow = row.firstElementChild;
        table.appendChild(newRow);
        attachRowHandlers(card, newRow, exerciseId);
        newRow.style.animation = 'reps-fade-up .35s cubic-bezier(.16,1,.3,1) both';
      });
    });
  }

  function attachRowHandlers(card, row, exerciseId) {
    const idx = Number(row.dataset.setIndex);
    const model = working[exerciseId][idx];
    row.querySelector('[data-field="weight"]').addEventListener('change', (e) => {
      const v = parseFloat(e.target.value) || 0;
      model.weightKg = U.weightToKg(v, D.user.units);
      refreshSummaryOnly();
    });
    row.querySelector('[data-field="reps"]').addEventListener('change', (e) => {
      model.reps = parseInt(e.target.value, 10) || 0;
      refreshSummaryOnly();
    });
    row.querySelector('.set-row__check').addEventListener('click', (e) => {
      model.completed = !model.completed;
      e.currentTarget.classList.toggle('is-done', model.completed);
      refreshSummaryOnly();
    });
  }

  function refreshSummaryOnly() {
    const screen = document.querySelector('.screen[data-screen="session"]');
    const valueEl = screen.querySelector('#session-summary .stat-card:nth-child(3) .stat-card__value');
    if (valueEl) valueEl.innerHTML = `${U.formatInt(currentVolume())}<span class="unit text-body-sm">kg</span>`;
    updateFinishBar(screen);
  }

  function updateFinishBar(screen) {
    const btn = screen.querySelector('#finish-workout-btn');
    const done = completedSetCount();
    const total = totalSetCount();
    btn.querySelector('.reps-btn__label').textContent = done === 0 ? 'Finish workout' : `Finish workout \u00b7 ${done}/${total} sets`;
  }

  function finish() {
    const workout = D.workoutById[activeWorkoutId];
    const done = completedSetCount();
    if (done === 0) {
      REPS.sheets.openModal({
        title: 'No sets logged yet',
        bodyHTML: 'Check off at least one set before finishing, or cancel to keep training.',
        actions: [
          { label: 'Keep training', kind: 'outlined' },
          { label: 'Finish anyway', kind: 'primary', onClick: () => completeSession(workout) },
        ],
      });
      return;
    }
    completeSession(workout);
  }

  function completeSession(workout) {
    const volume = currentVolume();
    const durationMin = Math.max(12, Math.round(workout.estimatedMinutes * (completedSetCount() / totalSetCount())));
    D.workoutSessions.unshift({
      id: U.uid('sess'), workoutId: workout.id, name: workout.name,
      date: new Date(), durationMin, volumeKg: Math.round(volume), prsHit: [],
    });
    REPS.sheets.openModal({
      title: 'Workout complete',
      bodyHTML: `${U.formatInt(volume)} kg lifted across ${completedSetCount()} sets. Nice work — that\u2019s logged in Progress.`,
      actions: [{ label: 'Done', kind: 'primary', onClick: () => REPS.nav.back() }],
    });
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="session"]');
    screen.querySelector('#session-back').addEventListener('click', () => REPS.nav.back());
    screen.querySelector('#finish-workout-btn').addEventListener('click', finish);
  }

  document.addEventListener('DOMContentLoaded', init);
  return { start };
})();
