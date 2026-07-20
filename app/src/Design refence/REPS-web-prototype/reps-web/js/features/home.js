/* ==========================================================================
   REPS — features/home.js
   Ports feature/home/HomeScreen.kt + HomeViewModel.kt. Card order and
   conditions are unchanged: header -> streak (only if active) -> today's
   workout or rest day -> quick actions -> weight -> motivation quote.
   ========================================================================== */
(function () {
  const { greetingFor, formatWeight } = REPS.utils;
  const C = REPS.components;
  const D = REPS.data;

  function unreadCount() { return D.notifications.filter(n => n.unread).length; }

  function renderHeader(headerEl) {
    const unread = unreadCount();
    headerEl.innerHTML = `
      <div style="min-width:0;">
        <p class="home-header__greeting text-body-md">${greetingFor()}</p>
        <h1 class="home-header__name text-headline-md truncate">${D.user.name}</h1>
      </div>
      <div class="home-header__spacer"></div>
      <button class="home-header__bell" id="home-bell" data-ripple type="button" aria-label="Notifications">
        <svg><use href="#icon-bell"/></svg>
        ${unread > 0 ? '<span class="home-header__bell-dot"></span>' : ''}
      </button>
      ${C.avatarInitial(D.user.name)}
    `;
    headerEl.querySelector('.avatar').id = 'home-avatar';
    headerEl.querySelector('.avatar').setAttribute('data-ripple', '');
    headerEl.querySelector('#home-bell').addEventListener('click', () => REPS.nav.push('notifications'));
    headerEl.querySelector('#home-avatar').addEventListener('click', () => REPS.nav.switchTab('profile'));
  }

  function renderStack(stackEl) {
    const workout = D.workoutById[D.todayWorkoutId];
    const muscles = D.muscleGroupsFor(workout).join(' & ');
    const weeklyDelta = D.weeklyDeltaKg();

    stackEl.innerHTML = [
      D.streak.current > 0 ? C.streakBadge(D.streak) : '',
      workout ? C.todayWorkoutCard(workout, muscles) : C.restDayCard(),
      C.quickActionsRow(Boolean(workout)),
      C.weightWidget(D.currentWeightKg, weeklyDelta, D.user.units),
      C.quoteCard('Daily Motivation', D.quoteForToday()),
    ].join('');

    // Stagger the reveal animation so cards cascade in rather than popping
    // together — cheap, and it reads as considerably more "alive".
    stackEl.querySelectorAll('.reveal').forEach((el, i) => {
      el.style.animationDelay = `${i * 60}ms`;
    });

    stackEl.querySelector('[data-action="open-workout"]')?.addEventListener('click', (e) => {
      if (e.target.closest('[data-action="start-workout"]')) return;
      if (workout) REPS.session.start(workout.id);
    });
    stackEl.querySelector('[data-action="start-workout"]')?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (workout) REPS.session.start(workout.id);
    });
    stackEl.querySelector('[data-action="quick-start"]')?.addEventListener('click', () => {
      if (workout) REPS.session.start(workout.id);
    });
    stackEl.querySelector('[data-action="quick-weight"]')?.addEventListener('click', () => openWeightTab());
    stackEl.querySelector('[data-action="quick-meal"]')?.addEventListener('click', () => REPS.nav.switchTab('nutrition'));
    stackEl.querySelector('[data-action="quick-timer"]')?.addEventListener('click', () => REPS.timer.open());
    stackEl.querySelector('[data-action="open-weight"]')?.addEventListener('click', () => openWeightTab());
  }

  function openWeightTab() {
    REPS.nav.switchTab('progress');
    setTimeout(() => REPS.progress?.openAddWeight?.(), 260);
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="home"]');
    const headerEl = screen.querySelector('#home-header');
    const stackEl = screen.querySelector('#home-stack');
    const scrollEl = screen.querySelector('#home-scroll');
    const ptrEl = screen.querySelector('[data-ptr]');

    function renderAll() {
      renderHeader(headerEl);
      renderStack(stackEl);
    }
    renderAll();

    REPS.nav.attachScrollHide('home', scrollEl);
    REPS.gestures.initPullToRefresh(scrollEl, ptrEl, {
      onRefresh: () => new Promise((resolve) => setTimeout(() => { renderAll(); resolve(); }, 700)),
    });
    REPS.nav.onTabReselect('home', () => scrollEl.scrollTo({ top: 0, behavior: 'smooth' }));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
