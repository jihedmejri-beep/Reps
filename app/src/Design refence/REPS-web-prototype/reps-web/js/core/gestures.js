/* ==========================================================================
   REPS — core/gestures.js
   Touch-first interaction primitives shared by every screen: ripple, sheet
   drag-to-dismiss, pull-to-refresh, and the scroll-direction nav hide/show.
   Pointer Events are used throughout so the same code covers touch, mouse
   and stylus without a separate code path per input type.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.gestures = (function () {
  const { clamp, rafThrottle } = REPS.utils;

  /* ---- Ripple, delegated from the document so newly-rendered markup
     never needs to re-wire a listener. Host element needs
     `data-ripple` + CSS position:relative; overflow:hidden. ---------------- */
  function initRipples(root = document) {
    root.addEventListener('pointerdown', (e) => {
      const host = e.target.closest('[data-ripple]');
      if (!host || host.hasAttribute('disabled') || host.classList.contains('is-disabled')) return;
      const rect = host.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height) * 1.6;
      const span = document.createElement('span');
      span.className = 'ripple';
      span.style.width = `${size}px`;
      span.style.height = `${size}px`;
      span.style.left = `${(e.clientX ?? rect.left + rect.width / 2) - rect.left - size / 2}px`;
      span.style.top = `${(e.clientY ?? rect.top + rect.height / 2) - rect.top - size / 2}px`;
      host.appendChild(span);
      span.addEventListener('animationend', () => span.remove());
      setTimeout(() => span.remove(), 700); // safety net
    }, { passive: true });
  }

  /* ---- Bottom sheet: drag the handle/header down to dismiss ------------- */
  function makeSheetDraggable(sheetEl, handleEl, { onDismiss, dismissAt = 120 } = {}) {
    let startY = 0;
    let currentY = 0;
    let dragging = false;

    function onDown(e) {
      dragging = true;
      startY = e.clientY;
      currentY = 0;
      sheetEl.classList.add('is-dragging');
      handleEl.setPointerCapture?.(e.pointerId);
    }
    function onMoveFn(e) {
      if (!dragging) return;
      currentY = Math.max(0, e.clientY - startY);
      sheetEl.style.transform = `translateY(${currentY}px)`;
    }
    function onUp() {
      if (!dragging) return;
      dragging = false;
      sheetEl.classList.remove('is-dragging');
      sheetEl.style.transform = '';
      if (currentY > dismissAt) {
        onDismiss?.();
      }
      currentY = 0;
    }
    handleEl.addEventListener('pointerdown', onDown);
    handleEl.addEventListener('pointermove', onMoveFn);
    handleEl.addEventListener('pointerup', onUp);
    handleEl.addEventListener('pointercancel', onUp);
    return () => {
      handleEl.removeEventListener('pointerdown', onDown);
      handleEl.removeEventListener('pointermove', onMoveFn);
      handleEl.removeEventListener('pointerup', onUp);
      handleEl.removeEventListener('pointercancel', onUp);
    };
  }

  /* ---- Pull to refresh ---------------------------------------------------
     scrollEl: the scrollable content column
     ptrEl: the fixed-height-0 indicator strip at its top (see .ptr in css)
     Reveals the REPS bars mark while pulling/loading — one spinner, used
     everywhere, instead of a generic native-looking activity indicator. */
  function initPullToRefresh(scrollEl, ptrEl, { onRefresh, maxPull = 92, triggerAt = 64 } = {}) {
    let startY = null;
    let pulling = false;
    let refreshing = false;
    const inner = ptrEl.querySelector('.ptr__inner');
    const barsHost = ptrEl.querySelector('.ptr__bars');
    const label = ptrEl.querySelector('.ptr__label');
    let barsCtrl = null;

    function setHeight(px) {
      ptrEl.style.height = `${px}px`;
    }

    function onDown(e) {
      if (scrollEl.scrollTop > 0 || refreshing) { startY = null; return; }
      startY = e.clientY;
      pulling = true;
    }
    function onMoveFn(e) {
      if (!pulling || startY === null || refreshing) return;
      const delta = e.clientY - startY;
      if (delta <= 0) { return; }
      if (scrollEl.scrollTop > 0) { pulling = false; setHeight(0); return; }
      const damped = Math.min(maxPull, delta * 0.5);
      setHeight(damped);
      if (label) label.textContent = damped >= triggerAt ? 'Release to refresh' : 'Pull to refresh';
      e.preventDefault?.();
    }
    function onUp() {
      if (!pulling) return;
      pulling = false;
      const height = parseFloat(ptrEl.style.height) || 0;
      if (height >= triggerAt && !refreshing) {
        refreshing = true;
        setHeight(56);
        if (label) label.textContent = 'Refreshing…';
        if (!barsCtrl && barsHost) barsCtrl = REPS.charts.mountBars(barsHost, { running: true });
        Promise.resolve(onRefresh?.()).then(() => {
          setTimeout(() => {
            setHeight(0);
            refreshing = false;
            barsCtrl?.stop();
            barsCtrl = null;
          }, 260);
        });
      } else {
        setHeight(0);
      }
    }

    scrollEl.addEventListener('pointerdown', onDown, { passive: true });
    scrollEl.addEventListener('pointermove', onMoveFn, { passive: false });
    scrollEl.addEventListener('pointerup', onUp, { passive: true });
    scrollEl.addEventListener('pointercancel', onUp, { passive: true });
  }

  /* ---- Scroll-direction driven nav hide/show ----------------------------- */
  function initScrollHideNav(scrollEl, navGetter) {
    let lastTop = 0;
    const handler = rafThrottle(() => {
      const nav = navGetter();
      if (!nav) return;
      const top = scrollEl.scrollTop;
      const delta = top - lastTop;
      if (top < 24) {
        nav.classList.remove('is-hidden');
      } else if (delta > 6) {
        nav.classList.add('is-hidden');
      } else if (delta < -6) {
        nav.classList.remove('is-hidden');
      }
      lastTop = top;
    });
    scrollEl.addEventListener('scroll', handler, { passive: true });
    return handler;
  }

  /* ---- Generic horizontal swipe detector (used for card dismiss / peek) -- */
  function onSwipe(el, { onLeft, onRight, threshold = 50 } = {}) {
    let startX = null, startY = null;
    el.addEventListener('pointerdown', (e) => { startX = e.clientX; startY = e.clientY; }, { passive: true });
    el.addEventListener('pointerup', (e) => {
      if (startX === null) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 1.5) {
        if (dx < 0) onLeft?.(); else onRight?.();
      }
      startX = null; startY = null;
    }, { passive: true });
  }

  return { initRipples, makeSheetDraggable, initPullToRefresh, initScrollHideNav, onSwipe };
})();
