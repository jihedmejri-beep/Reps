# REPS — Interactive Web Prototype

A mobile-first HTML/CSS/JS prototype of the REPS app, built to validate UI/UX
before the Jetpack Compose implementation. Plain HTML/CSS/JS, no build step,
no dependencies — open `index.html` directly in a browser.

## How to open it

**On a phone:** unzip, upload the folder somewhere you can serve it (or use
a tool like "Local HTML" / VS Code's Live Server), then open in Chrome for
the real edge-to-edge experience.

**On a computer:** double-click `index.html` to open it directly in Chrome.
Above ~900px wide it renders inside a phone-frame mockup so it previews
sensibly on a desktop monitor; narrow the window (or use Chrome's device
toolbar / an actual phone) to see the true full-bleed layout.

No server is required — everything is plain relative file references
(fonts, images, CSS, JS), so it works straight off the filesystem.

## What's ported exactly from the existing Android project

Colors, typography (Anton / Archivo Black / Poppins), the 3-tier responsive
spacing & type scale, corner radii, the five custom icons, the logo assets,
and every existing screen (Splash, Login, Sign Up, Home) — including the
splash bar-equalizer animation, which reuses the exact timing constants from
`RepsBarsModel.kt`.

## What's new

- **Floating bottom nav** — glass pill, spring-physics sliding indicator,
  expanding active tab, hide-on-scroll-down / reveal-on-scroll-up, touch ripple.
- **Progress screen** — hero/today ring, weekly completion, analytics with a
  month toggle, four custom SVG charts (weight, strength, frequency, muscle
  distribution), personal records, streak, achievements, and an activity
  timeline — all built from the existing card/chip vocabulary.
- **Workouts, Nutrition, Profile** — were placeholders in the Android
  codebase; built out here in the same visual language.
- Bottom sheets, modals, toasts, pull-to-refresh (reusing the splash bars as
  the refresh spinner), skeleton loading, and a working rest timer + workout
  session logger.
- Real browser-history integration, so the Android back gesture / Chrome's
  edge-swipe-back behaves like a native back stack, including closing sheets
  before it touches screen navigation.

## Try it

Email/password validation is real (matches `AuthValidation.kt`'s rules
exactly) — try submitting the login form empty, or a password under 8
characters on Sign Up. Any valid input signs you in (there's no backend).

## Project layout

```
index.html          entry point — open this
css/                 tokens, base, components, nav, overlays, animations, screens
js/core/             utils, sample data, charts, gestures, sheets, components
js/features/         one file per screen
js/navigation.js     floating nav + screen transitions + history
assets/              fonts + logo images copied from the Android project
```
