/* ==========================================================================
   REPS — app.js
   Final bootstrap. Placed last in body and deliberately NOT deferred to
   DOMContentLoaded: by the time this tag is reached, every screen and #device
   already exist in the DOM (they're above it in the document), so building
   the nav here runs *before* the feature modules' own DOMContentLoaded
   handlers fire — nav.buildNav() is guaranteed ready before anything tries
   to navigate.
   ========================================================================== */
(function () {
  REPS.gestures.initRipples(document);
  REPS.nav.buildNav(document.getElementById('device'));
})();
