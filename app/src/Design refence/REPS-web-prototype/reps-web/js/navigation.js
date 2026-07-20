/* ==========================================================================
   REPS — navigation.js
   Owns: the floating bottom nav (render + spring indicator + hide-on-scroll),
   the screen-transition system (tab / push / pop / reset), and real
   browser-history integration so the hardware/edge-swipe back gesture on
   Chrome Mobile behaves like a native Android back stack — including
   closing an open sheet/modal before it ever touches screen navigation.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.nav = (function () {
  const { Spring } = REPS.utils;

  const TABS = [
    { id: 'home', label: 'Home', icon: 'icon-home' },
    { id: 'progress', label: 'Progress', icon: 'icon-progress' },
    { id: 'workouts', label: 'Workouts', icon: 'icon-workouts' },
    { id: 'nutrition', label: 'Nutrition', icon: 'icon-nutrition' },
    { id: 'profile', label: 'Profile', icon: 'icon-profile' },
  ];
  const TAB_IDS = TABS.map(t => t.id);

  const state = {
    stack: [{ id: 'splash', isTab: false }],
    activeTab: 'home',
  };

  const enterHooks = {};
  const scrollTopHooks = {};
  let overlayStack = [];
  let pillEl = null, indicatorEl = null, navEl = null;
  let indicatorX = null, indicatorW = null;
  let scrollHideHandlers = {};

  function screenEl(id) { return document.querySelector(`.screen[data-screen="${id}"]`); }

  /* ------------------------------------------------------------------
     Low-level screen transition. Adds/removes the transition classes
     defined in css/animations.css, then settles into plain .is-active.
     ------------------------------------------------------------------ */
  function transition(fromId, toId, kind) {
    const fromEl = fromId ? screenEl(fromId) : null;
    const toEl = screenEl(toId);
    if (!toEl) return;

    if (fromEl === toEl) return;

    // The floating nav belongs to the five tabs only — splash, auth, and
    // full-bleed pushes (notifications, session) hide it, exactly like
    // RepsNavHost.kt's AnimatedVisibility(visible = currentTab != null).
    navEl?.classList.toggle('is-hidden', !TAB_IDS.includes(toId));

    toEl.classList.add('is-transitioning');
    if (fromEl) fromEl.classList.add('is-transitioning');

    const enterClass = {
      'tab-right': 'tab-enter-right', 'tab-left': 'tab-enter-left',
      push: 'push-enter', pop: 'pop-enter', fade: 'tab-enter-right',
    }[kind];
    const enterActiveClass = {
      'tab-right': 'tab-enter-active', 'tab-left': 'tab-enter-active',
      push: 'push-enter-active', pop: 'pop-enter-active', fade: 'tab-enter-active',
    }[kind];
    const exitActiveClass = {
      push: 'push-exit-active', pop: 'pop-exit-active',
    }[kind];

    toEl.classList.remove('is-active');
    toEl.classList.add(enterClass);
    toEl.style.zIndex = '6';
    // force reflow so the enter->enter-active transition actually animates
    void toEl.offsetWidth;

    toEl.classList.add('is-active');
    toEl.classList.add(enterActiveClass);

    if (fromEl && exitActiveClass) {
      fromEl.classList.add(exitActiveClass);
    }

    const cleanup = () => {
      toEl.classList.remove(enterClass, enterActiveClass, 'is-transitioning');
      toEl.style.zIndex = '';
      if (fromEl) {
        fromEl.classList.remove('is-active', exitActiveClass, 'is-transitioning', 'tab-enter-right', 'tab-enter-left', 'tab-enter-active', 'push-enter', 'push-enter-active', 'pop-enter', 'pop-enter-active');
      }
    };
    setTimeout(cleanup, 420);

    if (enterHooks[toId]) enterHooks[toId]();
  }

  function pushHistory(replace, extra) {
    const url = `#${state.stack[state.stack.length - 1].id}`;
    const payload = { depth: state.stack.length, ...extra };
    if (replace) history.replaceState(payload, '', url);
    else history.pushState(payload, '', url);
  }

  /* ------------------------------------------------------------------
     Public: tab switching
     ------------------------------------------------------------------ */
  function switchTab(tabId, { userInitiated = true } = {}) {
    if (!TAB_IDS.includes(tabId)) return;
    closeAllOverlaysImmediate();

    if (tabId === state.activeTab) {
      scrollTopHooks[tabId]?.();
      return;
    }
    const fromIdx = TAB_IDS.indexOf(state.activeTab);
    const toIdx = TAB_IDS.indexOf(tabId);
    const kind = toIdx > fromIdx ? 'tab-right' : 'tab-left';
    const fromId = state.activeTab;

    // Tabs are siblings: replace the current top-of-stack tab entry.
    const top = state.stack[state.stack.length - 1];
    if (top && top.isTab) state.stack[state.stack.length - 1] = { id: tabId, isTab: true };
    else state.stack.push({ id: tabId, isTab: true });

    state.activeTab = tabId;
    transition(fromId, tabId, kind);
    if (userInitiated) pushHistory(true);
    renderIndicator(tabId, true);
    syncTabClasses();
  }

  /* ------------------------------------------------------------------
     Public: push a detail screen on top of whatever is active
     ------------------------------------------------------------------ */
  function push(screenId) {
    closeAllOverlaysImmediate();
    const fromId = state.stack[state.stack.length - 1].id;
    state.stack.push({ id: screenId, isTab: false });
    transition(fromId, screenId, 'push');
    pushHistory(false);
  }

  /* ------------------------------------------------------------------
     Public: hard reset (splash -> login/signup -> home, sign out, etc.)
     ------------------------------------------------------------------ */
  function resetTo(screenId, { isTab = false } = {}) {
    closeAllOverlaysImmediate();
    const fromId = state.stack[state.stack.length - 1]?.id;
    state.stack = [{ id: screenId, isTab }];
    if (isTab) state.activeTab = screenId;
    transition(fromId, screenId, 'fade');
    pushHistory(true);
    if (isTab) { renderIndicator(screenId, false); syncTabClasses(); }
  }

  function back() { history.back(); }

  function closeAllOverlaysImmediate() {
    if (!overlayStack.length) return;
    overlayStack.forEach(o => o.closeFn(true));
    overlayStack = [];
  }

  /* ------------------------------------------------------------------
     Overlay (sheet/modal) registration — see openSheet/openModal below.
     ------------------------------------------------------------------ */
  function openOverlay(id, closeFn) {
    overlayStack.push({ id, closeFn });
    history.pushState({ overlay: id }, '', location.hash);
  }
  function requestCloseTop() {
    history.back();
  }

  window.addEventListener('popstate', () => {
    if (overlayStack.length) {
      const top = overlayStack.pop();
      top.closeFn(true);
      return;
    }
    if (state.stack.length <= 1) {
      // Trapped at the root screen — re-assert history so a back gesture
      // never leaves the prototype entirely.
      pushHistory(true);
      return;
    }
    const removed = state.stack.pop();
    const revealed = state.stack[state.stack.length - 1];
    transition(removed.id, revealed.id, 'pop');
    if (revealed.isTab) {
      state.activeTab = revealed.id;
      renderIndicator(revealed.id, false);
      syncTabClasses();
    }
  });

  /* ------------------------------------------------------------------
     Enter hooks: feature modules ask to be told when their screen
     becomes visible (to (re)play chart animations, refresh data, etc.)
     ------------------------------------------------------------------ */
  function onEnter(screenId, fn) { enterHooks[screenId] = fn; }
  function onTabReselect(tabId, fn) { scrollTopHooks[tabId] = fn; }

  /* ------------------------------------------------------------------
     Floating nav: render + spring-driven indicator + hide-on-scroll
     ------------------------------------------------------------------ */
  function buildNav(mountEl) {
    navEl = document.createElement('nav');
    navEl.className = 'reps-nav';
    navEl.setAttribute('aria-label', 'Primary');

    pillEl = document.createElement('div');
    pillEl.className = 'reps-nav__pill';

    indicatorEl = document.createElement('div');
    indicatorEl.className = 'reps-nav__indicator';
    pillEl.appendChild(indicatorEl);

    TABS.forEach((tab) => {
      const btn = document.createElement('button');
      btn.className = 'reps-nav__tab';
      btn.dataset.tab = tab.id;
      btn.dataset.ripple = 'true';
      btn.setAttribute('role', 'tab');
      btn.innerHTML = `
        <span class="reps-nav__tab-icon"><svg><use href="#${tab.icon}"/></svg></span>
        <span class="reps-nav__tab-label">${tab.label}</span>
      `;
      btn.addEventListener('click', () => switchTab(tab.id));
      pillEl.appendChild(btn);
    });

    navEl.appendChild(pillEl);
    mountEl.appendChild(navEl);
    // The app always launches on splash, which is not a tab — start hidden;
    // transition() reveals it the moment a tab screen is actually reached.
    navEl.classList.add('is-hidden');

    indicatorX = new Spring({ stiffness: 240, damping: 21 });
    indicatorW = new Spring({ stiffness: 240, damping: 24 });
    indicatorX.onUpdate = (v) => { indicatorEl.style.transform = `translateX(${v}px)`; };
    indicatorW.onUpdate = (v) => { indicatorEl.style.width = `${v}px`; };

    syncTabClasses();
    // First paint: place indicator without spring animation.
    requestAnimationFrame(() => renderIndicator(state.activeTab, false));
    window.addEventListener('resize', REPS.utils.debounce(() => renderIndicator(state.activeTab, false), 120));
  }

  function syncTabClasses() {
    if (!pillEl) return;
    pillEl.querySelectorAll('.reps-nav__tab').forEach((btn) => {
      btn.classList.toggle('is-active', btn.dataset.tab === state.activeTab);
      btn.setAttribute('aria-selected', String(btn.dataset.tab === state.activeTab));
    });
  }

  function renderIndicator(tabId, animate) {
    if (!pillEl) return;
    const btn = pillEl.querySelector(`.reps-nav__tab[data-tab="${tabId}"]`);
    if (!btn) return;
    // Let the label expand/collapse transition (width:max-width) settle
    // before measuring, so the indicator lands on the *final* tab width.
    requestAnimationFrame(() => {
      setTimeout(() => {
        const x = btn.offsetLeft;
        const w = btn.offsetWidth;
        if (animate && indicatorX && indicatorW) {
          indicatorX.to(x);
          indicatorW.to(w);
        } else if (indicatorX && indicatorW) {
          indicatorX.set(x);
          indicatorW.set(w);
        } else {
          indicatorEl.style.transform = `translateX(${x}px)`;
          indicatorEl.style.width = `${w}px`;
        }
      }, animate ? 0 : 20);
    });
  }

  function getNavEl() { return navEl; }
  function attachScrollHide(screenId, scrollEl) {
    if (scrollHideHandlers[screenId]) return;
    scrollHideHandlers[screenId] = true;
    REPS.gestures.initScrollHideNav(scrollEl, getNavEl);
  }
  function showNav() { navEl?.classList.remove('is-hidden'); }

  return {
    TABS, switchTab, push, resetTo, back,
    openOverlay, requestCloseTop,
    onEnter, onTabReselect, buildNav, attachScrollHide, showNav,
    getActiveTab: () => state.activeTab,
    getStackDepth: () => state.stack.length,
  };
})();
