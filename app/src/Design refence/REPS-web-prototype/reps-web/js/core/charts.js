/* ==========================================================================
   REPS — core/charts.js
   Two things live here:
     1. RepsBars — exact port of feature/splash/RepsBarsModel.kt +
        RepsBarsAnimation.kt. Reused for the splash mark AND as the
        pull-to-refresh spinner, so "REPS is thinking" always looks the same.
     2. A tiny dependency-free SVG line-chart (smoothed, animated draw-in,
        scrubbable) for the Progress screen. No chart library is loaded —
        this environment has no network access to fetch one reliably, and a
        bespoke renderer lets the line honour the exact brand green/ near-black
        system instead of a generic multi-colour default.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.charts = (function () {
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const el = (tag, attrs = {}) => {
    const n = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs).forEach(([k, v]) => n.setAttribute(k, v));
    return n;
  };

  /* ========================================================================
     RepsBars — ports RepsBarsModel.kt exactly:
       BAR_COUNT=3, CYCLE_MS=1080, RESTING=[.426,.723,1.0], SWING=.5,
       PHASE_LAG=.12, SLANT=.13. heightFraction() and drawBars() below are
       line-for-line translations of the Kotlin.
     ======================================================================== */
  const BARS_MODEL = {
    BAR_COUNT: 3,
    CYCLE_MS: 1080,
    RESTING: [0.426, 0.723, 1.0],
    SWING: 0.5,
    PHASE_LAG: 0.12,
    SLANT: 0.13,
    STATIC_PHASE: 0.25,
    BAR_TO_GAP: 6 / 14,
    heightFraction(barIndex, phase) {
      const angle = 2 * Math.PI * (phase - barIndex * this.PHASE_LAG);
      const scale = 1 + this.SWING * Math.sin(angle);
      return (this.RESTING[barIndex] * scale) / (1 + this.SWING);
    },
  };

  /**
   * Mounts an animated (or static) instance of the bars mark into `container`.
   * Returns a controller: { stop(), setColor(css) }.
   */
  function mountBars(container, { color = 'var(--reps-green)', running = true } = {}) {
    container.innerHTML = '';
    const svg = el('svg', { viewBox: '0 0 100 112', preserveAspectRatio: 'xMidYMax meet' });
    svg.style.width = '100%';
    svg.style.height = '100%';
    const paths = [0, 1, 2].map(() => {
      const p = el('path', { fill: color });
      svg.appendChild(p);
      return p;
    });
    container.appendChild(svg);

    function draw(phase) {
      const maxH = 112;
      const w = 100;
      const slantRoom = maxH * BARS_MODEL.SLANT;
      const baselineWidth = Math.max(1, w - slantRoom);
      const count = BARS_MODEL.BAR_COUNT;
      const barWidth = baselineWidth / (count + (count - 1) * BARS_MODEL.BAR_TO_GAP);
      const gap = barWidth * BARS_MODEL.BAR_TO_GAP;
      const baseline = maxH;

      for (let i = 0; i < count; i += 1) {
        const height = Math.max(1, BARS_MODEL.heightFraction(i, phase) * maxH);
        const left = i * (barWidth + gap);
        const lean = height * BARS_MODEL.SLANT;
        const d = `M${left},${baseline} L${left + barWidth},${baseline} L${left + barWidth + lean},${baseline - height} L${left + lean},${baseline - height} Z`;
        paths[i].setAttribute('d', d);
      }
    }

    let raf = null;
    let start = null;
    function frame(ts) {
      if (start === null) start = ts;
      const elapsed = (ts - start) % BARS_MODEL.CYCLE_MS;
      draw(elapsed / BARS_MODEL.CYCLE_MS);
      raf = requestAnimationFrame(frame);
    }
    if (running) {
      raf = requestAnimationFrame(frame);
    } else {
      draw(BARS_MODEL.STATIC_PHASE);
    }

    return {
      stop() { if (raf) cancelAnimationFrame(raf); raf = null; },
      setColor(c) { paths.forEach(p => p.setAttribute('fill', c)); },
    };
  }

  /* ========================================================================
     Line / area chart — Catmull-Rom smoothed, animated stroke draw-in,
     tap-and-drag scrub with a callback for the host card to show a value.
     ======================================================================== */

  // Convert a point series to a smooth SVG path using Catmull-Rom -> cubic Bezier.
  function smoothPath(points) {
    if (points.length < 2) return '';
    let d = `M${points[0].x},${points[0].y}`;
    for (let i = 0; i < points.length - 1; i += 1) {
      const p0 = points[i - 1] || points[i];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[i + 2] || p2;
      const c1x = p1.x + (p2.x - p0.x) / 6;
      const c1y = p1.y + (p2.y - p0.y) / 6;
      const c2x = p2.x - (p3.x - p1.x) / 6;
      const c2y = p2.y - (p3.y - p1.y) / 6;
      d += ` C${c1x},${c1y} ${c2x},${c2y} ${p2.x},${p2.y}`;
    }
    return d;
  }

  /**
   * renderLineChart(container, values, opts)
   *  values: number[] (already ordered oldest -> newest)
   *  opts.height: viewBox height (width is always 300, scales via CSS)
   *  opts.color: stroke colour
   *  opts.onScrub(index, value): called while the user drags across the chart
   *  opts.formatY(value): label formatter for the scrub readout (caller-side)
   * Returns { destroy(), replay() }.
   */
  function renderLineChart(container, values, opts = {}) {
    const {
      height = 120,
      color = 'var(--reps-green)',
      padding = 10,
      animate = true,
    } = opts;
    const width = 300;
    container.innerHTML = '';
    if (!values || values.length < 2) {
      const empty = document.createElement('div');
      empty.className = 'text-body-sm c-tertiary';
      empty.style.padding = '24px 0';
      empty.style.textAlign = 'center';
      empty.textContent = 'Not enough data yet.';
      container.appendChild(empty);
      return { destroy() {}, replay() {} };
    }

    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = (max - min) || 1;
    const usableH = height - padding * 2;
    const stepX = (width - padding * 2) / (values.length - 1);

    const points = values.map((v, i) => ({
      x: padding + i * stepX,
      y: padding + usableH - ((v - min) / span) * usableH,
      value: v,
    }));

    const svg = el('svg', { viewBox: `0 0 ${width} ${height}`, preserveAspectRatio: 'none' });
    svg.style.width = '100%';
    svg.style.height = `${height}px`;
    svg.style.display = 'block';
    svg.style.overflow = 'visible';
    svg.style.touchAction = 'pan-y';

    const gradId = `chartGrad-${Math.random().toString(36).slice(2)}`;
    const defs = el('defs');
    const grad = el('linearGradient', { id: gradId, x1: '0', y1: '0', x2: '0', y2: '1' });
    grad.appendChild(el('stop', { offset: '0%', 'stop-color': color, 'stop-opacity': 0.32 }));
    grad.appendChild(el('stop', { offset: '100%', 'stop-color': color, 'stop-opacity': 0 }));
    defs.appendChild(grad);
    svg.appendChild(defs);

    const linePath = smoothPath(points);
    const areaPath = `${linePath} L${points[points.length - 1].x},${height - padding} L${points[0].x},${height - padding} Z`;

    const area = el('path', { d: areaPath, fill: `url(#${gradId})`, stroke: 'none' });
    svg.appendChild(area);

    const baseline = el('line', {
      x1: padding, x2: width - padding, y1: height - padding, y2: height - padding,
      stroke: 'var(--reps-outline)', 'stroke-width': 1,
    });
    svg.appendChild(baseline);

    const line = el('path', {
      d: linePath, fill: 'none', stroke: color, 'stroke-width': 2.5,
      'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    });
    svg.appendChild(line);

    const endDot = el('circle', { cx: points[points.length - 1].x, cy: points[points.length - 1].y, r: 4, fill: color });
    svg.appendChild(endDot);
    const endHalo = el('circle', { cx: points[points.length - 1].x, cy: points[points.length - 1].y, r: 4, fill: 'none', stroke: color, 'stroke-width': 1.5, opacity: 0.5 });
    svg.appendChild(endHalo);

    // Scrub guide (hidden until interaction)
    const guide = el('line', { y1: padding, y2: height - padding, stroke: 'var(--reps-text-tertiary)', 'stroke-width': 1, 'stroke-dasharray': '3,3', opacity: 0 });
    const scrubDot = el('circle', { r: 5, fill: 'var(--reps-near-black)', stroke: color, 'stroke-width': 2.5, opacity: 0 });
    svg.appendChild(guide);
    svg.appendChild(scrubDot);

    container.appendChild(svg);

    function playIn() {
      const len = line.getTotalLength();
      [line].forEach(p => {
        p.style.transition = 'none';
        p.style.strokeDasharray = `${len}`;
        p.style.strokeDashoffset = `${len}`;
      });
      area.style.transition = 'none';
      area.style.opacity = '0';
      endDot.style.transition = 'none';
      endDot.style.opacity = '0';
      endHalo.style.opacity = '0';
      requestAnimationFrame(() => {
        line.style.transition = 'stroke-dashoffset 1.15s cubic-bezier(.16,1,.3,1)';
        line.style.strokeDashoffset = '0';
        area.style.transition = 'opacity 1s ease .25s';
        area.style.opacity = '1';
        endDot.style.transition = 'opacity .4s ease 1s';
        endDot.style.opacity = '1';
      });
    }
    if (animate) playIn(); else { area.style.opacity = '1'; endDot.style.opacity = '1'; }

    function nearestIndex(clientX) {
      const rect = svg.getBoundingClientRect();
      const relX = ((clientX - rect.left) / rect.width) * width;
      let closest = 0, bestDist = Infinity;
      points.forEach((p, i) => {
        const dist = Math.abs(p.x - relX);
        if (dist < bestDist) { bestDist = dist; closest = i; }
      });
      return closest;
    }
    function showScrub(i) {
      const p = points[i];
      guide.setAttribute('x1', p.x); guide.setAttribute('x2', p.x);
      guide.style.opacity = '1';
      scrubDot.setAttribute('cx', p.x); scrubDot.setAttribute('cy', p.y);
      scrubDot.style.opacity = '1';
      endDot.style.opacity = '0';
      endHalo.style.opacity = '0';
      if (opts.onScrub) opts.onScrub(i, p.value);
    }
    function hideScrub() {
      guide.style.opacity = '0';
      scrubDot.style.opacity = '0';
      endDot.style.opacity = '1';
      endHalo.style.opacity = '0.5';
      if (opts.onScrubEnd) opts.onScrubEnd();
    }

    let dragging = false;
    const onDown = (e) => { dragging = true; showScrub(nearestIndex(e.clientX)); };
    const onMove = (e) => { if (dragging) showScrub(nearestIndex(e.clientX)); };
    const onUp = () => { if (dragging) { dragging = false; hideScrub(); } };
    svg.addEventListener('pointerdown', onDown);
    svg.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);

    return {
      destroy() {
        svg.removeEventListener('pointerdown', onDown);
        svg.removeEventListener('pointermove', onMove);
        window.removeEventListener('pointerup', onUp);
      },
      replay: playIn,
    };
  }

  /**
   * Staggered reveal for a NodeList/array of elements whose height/width is
   * driven by an inline CSS custom prop or class toggle — used for the week
   * meter, frequency bars and ranked muscle-group bars.
   */
  function staggerReveal(items, applyFn, { delay = 45, startDelay = 80 } = {}) {
    items.forEach((item, i) => {
      setTimeout(() => applyFn(item, i), startDelay + i * delay);
    });
  }

  return { mountBars, renderLineChart, staggerReveal, BARS_MODEL };
})();
