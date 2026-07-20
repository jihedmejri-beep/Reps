/* ==========================================================================
   REPS — core/utils.js
   Small, dependency-free helpers. Formulas here are ports of:
     core/util/BmiCalculator.kt, BmrCalculator.kt, UnitConverter.kt, DateUtils.kt
     feature/auth/AuthValidation.kt
   Namespaced as window.REPS so every feature file shares one root without
   module bundling (this project intentionally ships with zero build step).
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.utils = (function () {

  const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
  const lerp = (a, b, t) => a + (b - a) * t;

  /* ---- id ---------------------------------------------------------- */
  let idCounter = 0;
  function uid(prefix) { idCounter += 1; return `${prefix}-${Date.now().toString(36)}-${idCounter}`; }

  /* ---- Unit conversion (UnitConverter.kt) --------------------------- */
  const KG_PER_LB = 0.45359237;
  const CM_PER_INCH = 2.54;

  const lbToKg = (lb) => lb * KG_PER_LB;
  const kgToLb = (kg) => kg / KG_PER_LB;
  const inchesToCm = (inches) => inches * CM_PER_INCH;
  const cmToInches = (cm) => cm / CM_PER_INCH;

  function displayWeight(kg, units) {
    return units === 'imperial' ? kgToLb(kg) : kg;
  }
  function weightToKg(value, units) {
    return units === 'imperial' ? lbToKg(value) : value;
  }
  function formatWeight(kg, units, decimals = 1) {
    return displayWeight(kg, units).toFixed(decimals);
  }
  function formatHeight(cm, units) {
    if (units === 'imperial') {
      const totalInches = Math.round(cmToInches(cm));
      return `${Math.floor(totalInches / 12)}' ${totalInches % 12}"`;
    }
    return `${Math.round(cm)} cm`;
  }
  function unitLabel(units) { return units === 'imperial' ? 'lb' : 'kg'; }

  /* ---- BMI (BmiCalculator.kt) ---------------------------------------- */
  function calcBmi(weightKg, heightCm) {
    const m = heightCm / 100;
    return weightKg / (m * m);
  }
  function bmiCategory(bmi) {
    if (bmi < 18.5) return { key: 'underweight', label: 'Underweight' };
    if (bmi < 25.0) return { key: 'healthy', label: 'Healthy' };
    if (bmi < 30.0) return { key: 'overweight', label: 'Overweight' };
    return { key: 'obese', label: 'Obese' };
  }

  /* ---- BMR: revised Harris-Benedict (BmrCalculator.kt) ---------------- */
  function calcBmr(sex, weightKg, heightCm, age) {
    if (sex === 'female') {
      return 655.1 + (9.563 * weightKg) + (1.850 * heightCm) - (4.676 * age);
    }
    return 66.5 + (13.75 * weightKg) + (5.003 * heightCm) - (6.755 * age);
  }

  /* ---- Greeting band (DateUtils.kt) ----------------------------------- */
  function greetingFor(date = new Date()) {
    const h = date.getHours();
    if (h >= 5 && h <= 11) return 'Good morning';
    if (h >= 12 && h <= 17) return 'Good afternoon';
    return 'Good evening';
  }

  /* ---- Validation (AuthValidation.kt) ---------------------------------- */
  const EMAIL_REGEX = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

  function emailError(email) {
    if (!email || !email.trim()) return 'Email is required.';
    if (!EMAIL_REGEX.test(email.trim())) return 'Enter a valid email address.';
    return null;
  }
  function passwordError(password, requireLength) {
    if (!password) return 'Password is required.';
    if (requireLength && password.length < 8) return 'Password must be at least 8 characters.';
    return null;
  }
  function nameError(name) {
    if (!name || !name.trim()) return 'Name is required.';
    return null;
  }

  /* ---- Formatting ------------------------------------------------------ */
  function formatInt(n) { return Math.round(n).toLocaleString('en-US'); }
  function formatCompact(n) {
    if (n >= 1000) return (n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(/\.0$/, '') + 'k';
    return `${Math.round(n)}`;
  }
  function pad2(n) { return n < 10 ? `0${n}` : `${n}`; }
  function formatClock(totalSeconds) {
    const s = Math.max(0, Math.round(totalSeconds));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    return h > 0 ? `${h}:${pad2(m)}:${pad2(sec)}` : `${pad2(m)}:${pad2(sec)}`;
  }

  const DAY_MS = 86400000;
  function daysAgo(n) { return new Date(Date.now() - n * DAY_MS); }
  function isSameDay(a, b) {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  }
  function shortDate(d) {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
  function relativeDay(d) {
    const today = new Date();
    if (isSameDay(d, today)) return 'Today';
    if (isSameDay(d, daysAgo(1))) return 'Yesterday';
    const diffDays = Math.round((today.setHours(0,0,0,0) - new Date(d).setHours(0,0,0,0)) / DAY_MS);
    if (diffDays < 7) return `${diffDays} days ago`;
    return shortDate(d);
  }
  const WEEKDAY_LETTERS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  /* ---- debounce / rAF throttle ------------------------------------------ */
  function rafThrottle(fn) {
    let scheduled = false;
    let lastArgs = null;
    return (...args) => {
      lastArgs = args;
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(() => { scheduled = false; fn(...lastArgs); });
    };
  }
  function debounce(fn, wait) {
    let t = null;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), wait); };
  }

  /* ---- Lightweight critically-damped-ish spring -------------------------
     Used to move the floating nav's indicator the same way the original
     RepsBottomBar animates its bias with Spring.DampingRatioLowBouncy /
     StiffnessMediumLow — a slight, tasteful overshoot rather than a linear
     ease. One instance per animated numeric value. */
  class Spring {
    constructor({ stiffness = 210, damping = 22, mass = 1, precision = 0.01 } = {}) {
      this.stiffness = stiffness;
      this.damping = damping;
      this.mass = mass;
      this.precision = precision;
      this.value = 0;
      this.target = 0;
      this.velocity = 0;
      this._raf = null;
      this.onUpdate = null;
      this.onRest = null;
    }
    set(value) { this.value = value; this.target = value; this.velocity = 0; this._notify(); return this; }
    to(target) {
      this.target = target;
      this._run();
      return this;
    }
    _notify() { if (this.onUpdate) this.onUpdate(this.value); }
    _run() {
      if (this._raf) return;
      let last = performance.now();
      const step = (now) => {
        const dt = Math.min(32, now - last) / 1000;
        last = now;
        const springForce = -this.stiffness * (this.value - this.target);
        const dampingForce = -this.damping * this.velocity;
        const acc = (springForce + dampingForce) / this.mass;
        this.velocity += acc * dt;
        this.value += this.velocity * dt;
        this._notify();
        const atRest = Math.abs(this.value - this.target) < this.precision && Math.abs(this.velocity) < this.precision;
        if (atRest) {
          this.value = this.target;
          this.velocity = 0;
          this._notify();
          this._raf = null;
          if (this.onRest) this.onRest();
          return;
        }
        this._raf = requestAnimationFrame(step);
      };
      this._raf = requestAnimationFrame(step);
    }
  }

  return {
    clamp, lerp, uid,
    lbToKg, kgToLb, inchesToCm, cmToInches, displayWeight, weightToKg, formatWeight, formatHeight, unitLabel,
    calcBmi, bmiCategory, calcBmr,
    greetingFor,
    emailError, passwordError, nameError,
    formatInt, formatCompact, pad2, formatClock,
    daysAgo, isSameDay, shortDate, relativeDay, WEEKDAY_LETTERS, DAY_MS,
    rafThrottle, debounce,
    Spring,
  };
})();
