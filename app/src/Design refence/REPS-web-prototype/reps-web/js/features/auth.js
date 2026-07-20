/* ==========================================================================
   REPS — features/auth.js
   Ports feature/auth/login/{LoginScreen,LoginViewModel}.kt and
   feature/auth/signup/{SignUpScreen,SignUpViewModel}.kt. No backend exists in
   this prototype, so "signing in" is a simulated network delay that always
   succeeds once client-side validation passes — the validation *states* are
   the thing worth demonstrating, not a fake auth server.
   ========================================================================== */
(function () {
  const { emailError, passwordError, nameError } = REPS.utils;

  function setFieldError(fieldEl, message) {
    fieldEl.classList.toggle('has-error', Boolean(message));
    const errEl = fieldEl.querySelector('.field__error');
    errEl.textContent = message || '';
  }
  function clearFieldError(fieldEl) { setFieldError(fieldEl, null); }

  function wirePasswordToggle(fieldEl) {
    const input = fieldEl.querySelector('.field__input');
    const toggle = fieldEl.querySelector('.field__trailing');
    if (!toggle) return;
    let visible = false;
    toggle.addEventListener('click', () => {
      visible = !visible;
      input.type = visible ? 'text' : 'password';
      toggle.innerHTML = `<svg><use href="#${visible ? 'icon-eye-off' : 'icon-eye'}"/></svg>`;
    });
  }

  function setLoading(btn, loading) {
    btn.classList.toggle('is-loading', loading);
    btn.disabled = loading;
  }

  /* ---------------------------------------------------------------- LOGIN */
  function initLogin() {
    const screen = document.querySelector('.screen[data-screen="login"]');
    const emailField = screen.querySelector('[data-field="email"]');
    const passField = screen.querySelector('[data-field="password"]');
    const emailInput = emailField.querySelector('.field__input');
    const passInput = passField.querySelector('.field__input');
    const formError = screen.querySelector('.auth-form-error');
    const submitBtn = screen.querySelector('#login-submit');
    const googleBtn = screen.querySelector('#login-google');
    const backBtn = screen.querySelector('#login-back');
    const forgotLink = screen.querySelector('#login-forgot');
    const createAccountLink = screen.querySelector('#login-create-account');

    wirePasswordToggle(passField);
    emailInput.addEventListener('input', () => { clearFieldError(emailField); formError.textContent = ''; });
    passInput.addEventListener('input', () => { clearFieldError(passField); formError.textContent = ''; });

    backBtn.addEventListener('click', () => REPS.nav.resetTo('splash'));
    createAccountLink.addEventListener('click', () => REPS.nav.push('signup'));

    forgotLink.addEventListener('click', () => {
      const err = emailError(emailInput.value);
      if (err) { setFieldError(emailField, err); return; }
      REPS.sheets.toast('Password reset email sent.');
    });

    function submit() {
      if (submitBtn.classList.contains('is-loading')) return;
      const eErr = emailError(emailInput.value);
      const pErr = passwordError(passInput.value, false); // login never enforces min length
      setFieldError(emailField, eErr);
      setFieldError(passField, pErr);
      if (eErr || pErr) return;

      setLoading(submitBtn, true);
      setTimeout(() => {
        setLoading(submitBtn, false);
        REPS.sheets.toast(`Welcome back, ${REPS.data.user.name.split(' ')[0]}.`);
        REPS.nav.resetTo('home', { isTab: true });
      }, 900);
    }
    submitBtn.addEventListener('click', submit);
    passInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
    googleBtn.addEventListener('click', () => {
      setLoading(submitBtn, false);
      setLoading(googleBtn, true);
      setTimeout(() => {
        setLoading(googleBtn, false);
        REPS.sheets.toast('Welcome back.');
        REPS.nav.resetTo('home', { isTab: true });
      }, 900);
    });

    REPS.nav.onEnter('login', () => { emailInput.focus({ preventScroll: true }); });
  }

  /* --------------------------------------------------------------- SIGNUP */
  function initSignup() {
    const screen = document.querySelector('.screen[data-screen="signup"]');
    const nameField = screen.querySelector('[data-field="name"]');
    const emailField = screen.querySelector('[data-field="email"]');
    const passField = screen.querySelector('[data-field="password"]');
    const nameInput = nameField.querySelector('.field__input');
    const emailInput = emailField.querySelector('.field__input');
    const passInput = passField.querySelector('.field__input');
    const formError = screen.querySelector('.auth-form-error');
    const submitBtn = screen.querySelector('#signup-submit');
    const backBtn = screen.querySelector('#signup-back');
    const signInLink = screen.querySelector('#signup-signin');
    const termsRow = screen.querySelector('#signup-terms-row');
    const checkbox = screen.querySelector('#signup-checkbox');

    wirePasswordToggle(passField);
    [nameInput, emailInput, passInput].forEach((input, i) => {
      const field = [nameField, emailField, passField][i];
      input.addEventListener('input', () => { clearFieldError(field); formError.textContent = ''; });
    });

    let termsAccepted = false;
    function syncSubmitEnabled() {
      submitBtn.classList.toggle('is-disabled', !termsAccepted);
      submitBtn.disabled = !termsAccepted;
    }
    termsRow.addEventListener('click', () => {
      termsAccepted = !termsAccepted;
      checkbox.classList.toggle('is-checked', termsAccepted);
      syncSubmitEnabled();
    });
    syncSubmitEnabled();

    backBtn.addEventListener('click', () => REPS.nav.back());
    signInLink.addEventListener('click', () => REPS.nav.back());

    function submit() {
      if (!termsAccepted || submitBtn.classList.contains('is-loading')) return;
      const nErr = nameError(nameInput.value);
      const eErr = emailError(emailInput.value);
      const pErr = passwordError(passInput.value, true); // new accounts enforce min length
      setFieldError(nameField, nErr);
      setFieldError(emailField, eErr);
      setFieldError(passField, pErr);
      if (nErr || eErr || pErr) return;

      setLoading(submitBtn, true);
      setTimeout(() => {
        setLoading(submitBtn, false);
        REPS.sheets.toast(`Account created. Let's build that streak, ${nameInput.value.trim().split(' ')[0]}.`);
        REPS.nav.resetTo('home', { isTab: true });
      }, 900);
    }
    submitBtn.addEventListener('click', submit);
    passInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
  }

  document.addEventListener('DOMContentLoaded', () => { initLogin(); initSignup(); });
})();
