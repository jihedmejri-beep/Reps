/* ==========================================================================
   REPS — features/profile.js
   Profile was a placeholder in RepsNavHost.kt; built new here from
   domain/model/User.kt's fields (sex, heightCm, age, goal, units, language)
   and the strings already reserved for it (profile_details, profile_goal_*,
   profile_units_*, profile_sign_out, etc).
   ========================================================================== */
(function () {
  const D = REPS.data;
  const C = REPS.components;
  const U = REPS.utils;

  const GOAL_LABEL = { cut: 'Cutting', bulk: 'Bulking', maintain: 'Maintain' };
  const SEX_LABEL = { male: 'Male', female: 'Female' };
  const LANGUAGES = [
    { code: 'en', label: 'English' },
    { code: 'ar', label: '\u0627\u0644\u0639\u0631\u0628\u064a\u0629' },
    { code: 'fr', label: 'Fran\u00e7ais' },
  ];

  function renderHero(root) {
    root.innerHTML = `
      ${C.avatarInitial(D.user.name)}
      <p class="profile-hero__name text-headline-sm">${D.user.name}</p>
      <p class="profile-hero__email text-body-sm">${D.user.email}</p>
      <div class="profile-hero__goal text-label-md">${C.icon('icon-flag')}<span>${GOAL_LABEL[D.user.goal]}</span></div>
    `;
  }

  function renderDetails(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:4px;">Your details</p>
      <div class="settings-list">
        ${C.listRow({ icon: 'icon-ruler', label: 'Height', value: U.formatHeight(D.user.heightCm, D.user.units), action: 'edit-height' })}
        ${C.listRow({ icon: 'icon-scale', label: 'Weight', value: `${U.formatWeight(D.currentWeightKg, D.user.units)} ${U.unitLabel(D.user.units)}`, action: 'edit-weight' })}
        ${C.listRow({ icon: 'icon-calendar', label: 'Age', value: `${D.user.age}`, action: 'edit-age' })}
        ${C.listRow({ icon: 'icon-person', label: 'Sex', value: SEX_LABEL[D.user.sex], action: 'edit-sex' })}
        ${C.listRow({ icon: 'icon-flag', label: 'Goal', value: GOAL_LABEL[D.user.goal], action: 'edit-goal' })}
      </div>
    `;
  }

  function renderBodyMetrics(root) {
    const bmi = U.calcBmi(D.currentWeightKg, D.user.heightCm);
    const bmr = U.calcBmr(D.user.sex, D.currentWeightKg, D.user.heightCm, D.user.age);
    const cat = U.bmiCategory(bmi);
    root.innerHTML = `
      <div class="stat-grid">
        ${C.statCard({ icon: 'icon-target', label: 'BMI', value: bmi.toFixed(1), deltaText: cat.label, deltaKind: 'flat' })}
        ${C.statCard({ icon: 'icon-flame', label: 'BMR', value: formatInt0(bmr), unit: 'kcal/day' })}
      </div>
    `;
  }
  function formatInt0(n) { return Math.round(n).toLocaleString('en-US'); }

  function renderPreferences(root) {
    root.innerHTML = `
      <p class="text-title-sm c-primary" style="margin-bottom:4px;">Preferences</p>
      <div class="settings-list">
        <div class="list-row">
          <div class="list-row__icon">${C.icon('icon-scale')}</div>
          <div class="list-row__body"><p class="list-row__label text-body-lg">Units</p><p class="list-row__sub text-body-sm">${D.user.units === 'metric' ? 'Metric (kg, cm)' : 'Imperial (lb, in)'}</p></div>
          <button class="toggle ${D.user.units === 'imperial' ? 'is-on' : ''}" id="units-toggle" type="button" aria-label="Toggle units"><div class="toggle__thumb"></div></button>
        </div>
        ${C.listRow({ icon: 'icon-globe', label: 'Language', value: LANGUAGES.find(l => l.code === D.user.language).label, action: 'edit-language' })}
        ${C.listRow({ icon: 'icon-settings', label: 'Account settings', action: 'account-settings' })}
      </div>
    `;
    root.querySelector('#units-toggle').addEventListener('click', () => {
      D.user.units = D.user.units === 'metric' ? 'imperial' : 'metric';
      renderPreferences(root);
      renderDetails(document.getElementById('profile-details'));
      renderBodyMetrics(document.getElementById('profile-metrics'));
    });
  }

  function editValueSheet({ title, label, value, unit = '', onSave, inputType = 'number' }) {
    let handle = null;
    handle = REPS.sheets.open({
      title,
      bodyHTML: `
        <div class="field" id="edit-field">
          <label class="field__label text-eyebrow">${label}</label>
          <div class="field__control">
            <input class="field__input" id="edit-input" type="${inputType}" inputmode="${inputType === 'number' ? 'decimal' : 'text'}" value="${value}"/>
            ${unit ? `<span class="text-body-sm c-secondary">${unit}</span>` : ''}
          </div>
          <p class="field__error text-body-sm"></p>
        </div>
        <button class="reps-btn reps-btn--primary" data-ripple type="button" id="edit-save" style="margin-top:18px;">
          <span class="reps-btn__label text-button-label">Save changes</span>
        </button>
      `,
      onOpen(body) {
        const input = body.querySelector('#edit-input');
        setTimeout(() => { input.focus(); input.select?.(); }, 260);
        body.querySelector('#edit-save').addEventListener('click', () => {
          if (!input.value.toString().trim()) {
            body.querySelector('#edit-field').classList.add('has-error');
            body.querySelector('.field__error').textContent = 'This field can\u2019t be empty.';
            return;
          }
          onSave(input.value);
          handle.close();
          REPS.sheets.toast('Saved.');
        });
      },
    });
  }

  function wireDetailActions(root) {
    root.addEventListener('click', (e) => {
      const row = e.target.closest('[data-action]');
      if (!row) return;
      const action = row.dataset.action;
      if (action === 'edit-height') {
        editValueSheet({
          title: 'Height', label: `Height (${D.user.units === 'metric' ? 'cm' : 'in'})`,
          value: D.user.units === 'metric' ? Math.round(D.user.heightCm) : Math.round(U.cmToInches(D.user.heightCm)),
          onSave: (v) => { D.user.heightCm = D.user.units === 'metric' ? Number(v) : U.inchesToCm(Number(v)); renderAll(); },
        });
      } else if (action === 'edit-weight') {
        REPS.progress?.openAddWeight ? REPS.progress.openAddWeight() : null;
      } else if (action === 'edit-age') {
        editValueSheet({ title: 'Age', label: 'Age', value: D.user.age, onSave: (v) => { D.user.age = Number(v); renderAll(); } });
      } else if (action === 'edit-sex') {
        openPickerSheet('Sex', [{ v: 'male', l: 'Male' }, { v: 'female', l: 'Female' }], D.user.sex, (v) => { D.user.sex = v; renderAll(); });
      } else if (action === 'edit-goal') {
        openPickerSheet('Goal', [{ v: 'cut', l: 'Cutting' }, { v: 'bulk', l: 'Bulking' }, { v: 'maintain', l: 'Maintain' }], D.user.goal, (v) => { D.user.goal = v; renderAll(); });
      } else if (action === 'edit-language') {
        openPickerSheet('Language', LANGUAGES.map(l => ({ v: l.code, l: l.label })), D.user.language, (v) => {
          D.user.language = v;
          renderPreferences(document.getElementById('profile-prefs'));
          REPS.sheets.toast(v === 'en' ? 'Language set to English.' : 'This prototype demonstrates English only.');
        });
      } else if (action === 'account-settings') {
        REPS.sheets.open({ title: 'Account settings', bodyHTML: `<p class="text-body-md c-secondary">Password, email and connected sign-in methods live here in the full app.</p>` });
      }
    });
  }

  function openPickerSheet(title, options, current, onPick) {
    let handle = null;
    handle = REPS.sheets.open({
      title,
      bodyHTML: `<div class="settings-list">${options.map(o => `
        <div class="list-row is-tappable" data-ripple data-val="${o.v}">
          <div class="list-row__body"><p class="list-row__label text-body-lg">${o.l}</p></div>
          ${o.v === current ? `<span style="color:var(--reps-green);width:20px;height:20px;">${C.icon('icon-check')}</span>` : ''}
        </div>`).join('')}</div>`,
      onOpen(body) {
        body.querySelectorAll('[data-val]').forEach((row) => {
          row.addEventListener('click', () => { onPick(row.dataset.val); handle.close(); });
        });
      },
    });
  }

  function renderAll() {
    renderHero(document.getElementById('profile-hero'));
    renderDetails(document.getElementById('profile-details'));
    renderBodyMetrics(document.getElementById('profile-metrics'));
    renderPreferences(document.getElementById('profile-prefs'));
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="profile"]');
    const scrollEl = screen.querySelector('#profile-scroll');
    const signOutBtn = screen.querySelector('#sign-out-btn');

    renderAll();
    // Delegated from the scroll container, not #profile-details alone, so it
    // also catches the Language / Account settings rows rendered separately
    // into #profile-prefs.
    wireDetailActions(scrollEl);

    signOutBtn.addEventListener('click', () => {
      REPS.sheets.openModal({
        title: 'Sign out?',
        bodyHTML: `You can sign back in any time — your data stays on this device.`,
        actions: [
          { label: 'Cancel', kind: 'outlined' },
          { label: 'Sign out', kind: 'primary', onClick: () => { REPS.sheets.toast('Signed out.'); REPS.nav.resetTo('splash'); } },
        ],
      });
    });

    REPS.nav.attachScrollHide('profile', scrollEl);
    REPS.nav.onTabReselect('profile', () => scrollEl.scrollTo({ top: 0, behavior: 'smooth' }));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
