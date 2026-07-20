/* ==========================================================================
   REPS — features/notifications.js
   Ports the notifications_* strings reserved in strings.xml. Pushed on top
   of whichever tab is active (opened from Home's bell icon), not a tab
   itself — matching Routes.NOTIFICATIONS in Destinations.kt.
   ========================================================================== */
(function () {
  const D = REPS.data;
  const C = REPS.components;

  function render(root) {
    if (!D.notifications.length) {
      root.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">${C.icon('icon-bell')}</div>
          <p class="empty-state__title text-title-sm">Nothing new.</p>
        </div>`;
      return;
    }
    root.innerHTML = D.notifications.map(n => `
      <div class="notif-row" data-ripple data-id="${n.id}">
        <div class="notif-row__icon">${C.icon(n.icon)}</div>
        <div class="notif-row__body">
          <p class="notif-row__title text-body-lg">${n.title}</p>
          <p class="notif-row__desc text-body-sm">${n.desc}</p>
          <p class="notif-row__time text-label-sm">${n.time}</p>
        </div>
        ${n.unread ? '<span class="notif-row__dot"></span>' : ''}
      </div>`).join('');

    root.querySelectorAll('.notif-row').forEach((row) => {
      row.addEventListener('click', () => {
        const n = D.notifications.find(x => x.id === row.dataset.id);
        if (n) { n.unread = false; row.querySelector('.notif-row__dot')?.remove(); }
      });
    });
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="notifications"]');
    const listEl = screen.querySelector('#notifications-list');
    const backBtn = screen.querySelector('#notifications-back');
    backBtn.addEventListener('click', () => REPS.nav.back());
    REPS.nav.onEnter('notifications', () => render(listEl));
    render(listEl);
  }

  document.addEventListener('DOMContentLoaded', init);
})();
