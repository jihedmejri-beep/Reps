/* ==========================================================================
   REPS — features/nutrition.js
   Nutrition was a placeholder in RepsNavHost.kt; built new here using the
   strings already reserved for it (nutrition_today/nutrition_target/
   nutrition_add_meal/nutrition_add_food/nutrition_empty) and domain/model's
   Macros shape (calories, protein, carbs, fat).
   ========================================================================== */
(function () {
  const D = REPS.data;
  const C = REPS.components;
  const { formatInt } = REPS.utils;

  function totalsForToday() {
    return D.meals.reduce((acc, meal) => {
      const m = D.mealMacros(meal);
      acc.cal += m.cal; acc.protein += m.protein; acc.carbs += m.carbs; acc.fat += m.fat;
      return acc;
    }, { cal: 0, protein: 0, carbs: 0, fat: 0 });
  }

  function renderSummary(root) {
    const totals = totalsForToday();
    const target = D.nutritionTarget;
    const pct = Math.min(100, Math.round((totals.cal / target.cal) * 100));
    const CIRC = 251.2;

    root.innerHTML = `
      <div class="surface-card macro-ring-card reveal">
        <div class="hero-ring-wrap">
          <svg viewBox="0 0 96 96">
            <circle class="hero-ring-track" cx="48" cy="48" r="40"/>
            <circle class="hero-ring-fill" id="cal-ring-fill" cx="48" cy="48" r="40" stroke-dasharray="${CIRC}" stroke-dashoffset="${CIRC}"/>
          </svg>
          <div class="hero-ring-center">
            <span class="val text-title-md">${formatInt(totals.cal)}</span>
            <span class="lbl text-label-sm">of ${formatInt(target.cal)}</span>
          </div>
        </div>
        <div class="macro-bars">
          ${macroBar('Protein', totals.protein, target.protein)}
          ${macroBar('Carbs', totals.carbs, target.carbs)}
          ${macroBar('Fat', totals.fat, target.fat)}
        </div>
      </div>
    `;
    requestAnimationFrame(() => {
      const fill = root.querySelector('#cal-ring-fill');
      if (fill) fill.style.strokeDashoffset = `${CIRC * (1 - pct / 100)}`;
      root.querySelectorAll('.macro-bar-row__fill').forEach((el) => { el.style.width = `${el.dataset.target}%`; });
    });
  }

  function macroBar(label, value, target) {
    const pct = Math.min(100, Math.round((value / target) * 100));
    return `
      <div class="macro-bar-row">
        <span class="macro-bar-row__label text-label-sm">${label}</span>
        <div class="macro-bar-row__track"><div class="macro-bar-row__fill" data-target="${pct}"></div></div>
        <span class="macro-bar-row__val text-label-sm">${formatInt(value)}/${target}g</span>
      </div>`;
  }

  function renderMeals(root) {
    if (!D.meals.length) {
      root.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">${C.icon('icon-nutrition')}</div>
          <p class="empty-state__title text-title-sm">No meals logged today.</p>
        </div>`;
      return;
    }
    root.innerHTML = D.meals.map((meal, i) => {
      const m = D.mealMacros(meal);
      return `
        <div class="surface-card meal-card reveal" style="animation-delay:${i * 70}ms;">
          <div class="meal-card__head">
            <span class="meal-card__name text-title-sm">${meal.name}</span>
            <span class="meal-card__cals text-label-md">${formatInt(m.cal)} kcal</span>
          </div>
          <div>
            ${meal.items.map(it => `
              <div class="food-row">
                <span class="food-row__name text-body-md">${it.name}<span class="food-row__grams text-label-sm">${it.grams}g</span></span>
                <span class="food-row__cals text-label-sm">${formatInt(it.cal * it.grams / 100)} kcal</span>
              </div>`).join('')}
          </div>
        </div>`;
    }).join('');
  }

  function openAddMealSheet() {
    let handle = null;
    const draftItems = [];
    handle = REPS.sheets.open({
      title: 'Add meal',
      bodyHTML: `
        <div class="field">
          <div class="field__control">
            <input class="field__input" id="meal-name" type="text" placeholder="Meal name (e.g. Dinner)"/>
          </div>
        </div>
        <p class="text-eyebrow c-green" style="margin:18px 0 10px;">Add ingredient</p>
        <div style="display:flex;gap:8px;">
          <div class="field" style="flex:2;"><div class="field__control" style="min-height:46px;"><input class="field__input" id="food-name" placeholder="Ingredient"/></div></div>
          <div class="field" style="flex:1;"><div class="field__control" style="min-height:46px;"><input class="field__input" id="food-grams" type="number" inputmode="decimal" placeholder="Grams"/></div></div>
        </div>
        <button class="reps-btn reps-btn--outlined" data-ripple type="button" id="food-add" style="margin-top:10px;min-height:44px;">
          ${C.icon('icon-plus', 'reps-btn__icon')}<span class="reps-btn__label text-button-label">Add ingredient</span>
        </button>
        <div id="draft-food-list" style="margin-top:6px;"></div>
        <button class="reps-btn reps-btn--primary" data-ripple type="button" id="meal-save" style="margin-top:20px;">
          <span class="reps-btn__label text-button-label">Save meal</span><span class="reps-btn__spinner"></span>
        </button>
      `,
      onOpen(body) {
        const listEl = body.querySelector('#draft-food-list');
        function renderDraft() {
          listEl.innerHTML = draftItems.map((it, i) => `
            <div class="food-row"><span class="food-row__name text-body-sm">${it.name}<span class="food-row__grams text-label-sm">${it.grams}g</span></span>
            <button class="reps-btn__icon" data-remove="${i}" style="width:16px;height:16px;color:var(--reps-text-tertiary);">${C.icon('icon-close')}</button></div>
          `).join('');
          listEl.querySelectorAll('[data-remove]').forEach(btn => btn.addEventListener('click', () => {
            draftItems.splice(Number(btn.dataset.remove), 1);
            renderDraft();
          }));
        }
        body.querySelector('#food-add').addEventListener('click', () => {
          const name = body.querySelector('#food-name').value.trim();
          const grams = parseFloat(body.querySelector('#food-grams').value);
          if (!name || !grams) { REPS.sheets.toast('Add a name and gram amount.', { kind: 'error' }); return; }
          // Reasonable per-100g estimate so the running total still means
          // something, since this prototype has no nutrition database to query.
          draftItems.push({ name, grams, cal: 165, protein: 12, carbs: 14, fat: 5 });
          body.querySelector('#food-name').value = '';
          body.querySelector('#food-grams').value = '';
          renderDraft();
        });
        body.querySelector('#meal-save').addEventListener('click', () => {
          const name = body.querySelector('#meal-name').value.trim() || 'Meal';
          if (!draftItems.length) { REPS.sheets.toast('Add at least one ingredient.', { kind: 'error' }); return; }
          D.meals.push({ id: REPS.utils.uid('meal'), name, items: draftItems.slice() });
          handle.close();
          REPS.sheets.toast('Meal logged.');
          renderSummary(document.getElementById('nutrition-summary'));
          renderMeals(document.getElementById('nutrition-meals'));
        });
      },
    });
  }

  function init() {
    const screen = document.querySelector('.screen[data-screen="nutrition"]');
    const scrollEl = screen.querySelector('#nutrition-scroll');
    const summaryEl = screen.querySelector('#nutrition-summary');
    const mealsEl = screen.querySelector('#nutrition-meals');
    const addBtn = screen.querySelector('#add-meal-btn');

    renderSummary(summaryEl);
    renderMeals(mealsEl);
    addBtn.addEventListener('click', openAddMealSheet);

    REPS.nav.attachScrollHide('nutrition', scrollEl);
    REPS.nav.onTabReselect('nutrition', () => scrollEl.scrollTo({ top: 0, behavior: 'smooth' }));
  }

  document.addEventListener('DOMContentLoaded', init);
})();
