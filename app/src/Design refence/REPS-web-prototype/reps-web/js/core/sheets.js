/* ==========================================================================
   REPS — core/sheets.js
   One factory for every bottom sheet and centered modal in the app, so drag-
   to-dismiss, scrim-tap-to-dismiss, and the hardware-back integration only
   need to be built once. See navigation.js openOverlay/requestCloseTop.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.sheets = (function () {
  function root() {
    let r = document.getElementById('overlay-root');
    if (!r) {
      r = document.createElement('div');
      r.id = 'overlay-root';
      r.className = 'overlay-root';
      document.getElementById('device').appendChild(r);
    }
    return r;
  }

  /**
   * open({ title, bodyHTML, onOpen(bodyEl), onClose() }) -> { close(), bodyEl, scrimEl }
   */
  function open({ title = '', bodyHTML = '', onOpen, onClose } = {}) {
    const scrim = document.createElement('div');
    scrim.className = 'scrim';
    const sheet = document.createElement('div');
    sheet.className = 'sheet';
    sheet.innerHTML = `
      <div class="sheet__handle-row"><div class="sheet__handle"></div></div>
      <div class="sheet__head">
        <h3 class="sheet__title text-title-lg">${title}</h3>
        <button class="sheet__close" data-ripple type="button" aria-label="Close">
          <svg><use href="#icon-close"/></svg>
        </button>
      </div>
      <div class="sheet__body">${bodyHTML}</div>
    `;
    root().appendChild(scrim);
    root().appendChild(sheet);

    let closed = false;
    function doClose() {
      if (closed) return;
      closed = true;
      sheet.classList.remove('is-open');
      scrim.classList.remove('is-visible');
      setTimeout(() => { scrim.remove(); sheet.remove(); }, 380);
      onClose?.();
    }
    // fromPopstate=true means history already moved; fromPopstate=false/undefined
    // means a UI action (X button, scrim tap, drag) wants to close — that path
    // must go through history.back() so the stack stays truthful.
    function closeFn(fromHistory) {
      if (fromHistory) doClose();
      else REPS.nav.requestCloseTop();
    }

    scrim.addEventListener('click', () => closeFn(false));
    sheet.querySelector('.sheet__close').addEventListener('click', () => closeFn(false));
    REPS.gestures.makeSheetDraggable(sheet, sheet.querySelector('.sheet__handle-row'), {
      onDismiss: () => closeFn(false),
    });

    REPS.nav.openOverlay('sheet', closeFn);

    requestAnimationFrame(() => {
      scrim.classList.add('is-visible');
      sheet.classList.add('is-open');
    });

    const bodyEl = sheet.querySelector('.sheet__body');
    onOpen?.(bodyEl);

    return { close: () => closeFn(false), bodyEl, sheetEl: sheet };
  }

  /**
   * openModal({ title, bodyHTML, actions:[{label, kind:'primary'|'outlined', onClick}] })
   */
  function openModal({ title = '', bodyHTML = '', actions = [] } = {}) {
    const scrim = document.createElement('div');
    scrim.className = 'scrim';
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.innerHTML = `
      <div class="modal__card">
        <h3 class="modal__title text-title-lg">${title}</h3>
        <div class="modal__body text-body-md">${bodyHTML}</div>
        <div class="modal__actions"></div>
      </div>
    `;
    root().appendChild(scrim);
    root().appendChild(modal);

    let closed = false;
    function doClose() {
      if (closed) return;
      closed = true;
      modal.classList.remove('is-open');
      scrim.classList.remove('is-visible');
      setTimeout(() => { scrim.remove(); modal.remove(); }, 300);
    }
    function closeFn(fromHistory) {
      if (fromHistory) doClose(); else REPS.nav.requestCloseTop();
    }
    scrim.addEventListener('click', () => closeFn(false));
    REPS.nav.openOverlay('modal', closeFn);

    const actionsEl = modal.querySelector('.modal__actions');
    actions.forEach((a) => {
      const btn = document.createElement('button');
      btn.className = `reps-btn reps-btn--${a.kind === 'outlined' ? 'outlined' : 'primary'}`;
      btn.dataset.ripple = 'true';
      btn.innerHTML = `<span class="reps-btn__label text-button-label">${a.label}</span>`;
      btn.addEventListener('click', () => { a.onClick?.(); closeFn(false); });
      actionsEl.appendChild(btn);
    });

    requestAnimationFrame(() => {
      scrim.classList.add('is-visible');
      modal.classList.add('is-open');
    });

    return { close: () => closeFn(false) };
  }

  function toast(message, { kind = 'success', duration = 2600 } = {}) {
    let stack = document.getElementById('toast-stack');
    if (!stack) {
      stack = document.createElement('div');
      stack.id = 'toast-stack';
      stack.className = 'toast-stack';
      document.getElementById('device').appendChild(stack);
    }
    const t = document.createElement('div');
    t.className = `toast toast--${kind}`;
    const icon = kind === 'error' ? 'icon-close' : 'icon-check';
    t.innerHTML = `<svg><use href="#${icon}"/></svg><span class="text-label-md">${message}</span>`;
    stack.appendChild(t);
    setTimeout(() => {
      t.classList.add('is-leaving');
      setTimeout(() => t.remove(), 320);
    }, duration);
  }

  return { open, openModal, toast };
})();
