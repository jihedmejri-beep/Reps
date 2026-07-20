/* ==========================================================================
   REPS — core/data.js
   Stand-in content, same spirit as data/fake/SampleData.kt: "Alex Rivera, a
   12-day streak, Push Day at 78.4kg" so every screen agrees with every other
   screen. Nothing here calls a network — it's a prototype, not production.
   ========================================================================== */
window.REPS = window.REPS || {};

REPS.data = (function () {
  const { daysAgo } = REPS.utils;

  /* Deterministic PRNG (mulberry32) — stable across reloads, mirroring the
     spirit of Kotlin's Random(seed = 42) without needing the same engine. */
  function mulberry32(seed) {
    return function () {
      seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
      let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  const rng = mulberry32(42);

  /* ---- User (SampleData.user) ---------------------------------------- */
  const user = {
    uid: 'sample-uid',
    name: 'Alex Rivera',
    email: 'alex@reps.app',
    sex: 'male',
    heightCm: 180.0,
    age: 28,
    goal: 'cut',
    units: 'metric',
    language: 'en',
    streakCount: 12,
    longestStreak: 27,
  };

  /* ---- Exercises (SampleData.exercises) ------------------------------ */
  const exercises = [
    { id: 'bench-press', name: 'Barbell Bench Press', muscleGroup: 'Chest', equipment: 'Barbell', difficulty: 'Intermediate',
      description: 'Lie flat, grip just wider than shoulder width, lower the bar to mid-chest under control, then drive it back up without bouncing.',
      mistakes: ['Flaring the elbows to 90 degrees, which strains the shoulder joint.', 'Bouncing the bar off the chest instead of pausing under control.', 'Lifting the hips off the bench to force the last rep.'] },
    { id: 'incline-db-press', name: 'Incline Dumbbell Press', muscleGroup: 'Chest', equipment: 'Dumbbells', difficulty: 'Beginner',
      description: 'Set the bench to roughly 30 degrees and press the dumbbells from the outer chest to directly over the collarbone.',
      mistakes: ['Setting the incline too steep, turning it into a shoulder press.', 'Clashing the dumbbells together at the top and losing tension.'] },
    { id: 'cable-fly', name: 'Cable Fly', muscleGroup: 'Chest', equipment: 'Cable machine', difficulty: 'Beginner',
      description: 'With a slight bend in the elbows, bring both handles together in front of the sternum and squeeze before returning slowly.',
      mistakes: ['Bending the elbows through the rep, which turns it into a press.', 'Going so heavy the range of motion collapses.'] },
    { id: 'overhead-press', name: 'Overhead Press', muscleGroup: 'Shoulders', equipment: 'Barbell', difficulty: 'Intermediate',
      description: 'Press the bar from the front rack to lockout overhead, moving the head back through as the bar passes the face.',
      mistakes: ['Leaning back excessively and turning it into an incline press.', 'Stopping short of full lockout.'] },
    { id: 'lateral-raise', name: 'Lateral Raise', muscleGroup: 'Shoulders', equipment: 'Dumbbells', difficulty: 'Beginner',
      description: 'Raise the dumbbells out to the sides to shoulder height, leading with the elbows.',
      mistakes: ['Swinging the weight up with momentum from the hips.', 'Raising above shoulder height, which shifts the work to the traps.'] },
    { id: 'triceps-pushdown', name: 'Triceps Pushdown', muscleGroup: 'Arms', equipment: 'Cable machine', difficulty: 'Beginner',
      description: 'Keep the elbows pinned to the ribs and extend the forearms down until the arms are straight.',
      mistakes: ['Letting the elbows drift forward and away from the body.', 'Leaning over the bar to push with bodyweight.'] },
    { id: 'deadlift', name: 'Conventional Deadlift', muscleGroup: 'Back', equipment: 'Barbell', difficulty: 'Advanced',
      description: 'Hinge at the hips, keep the bar against the legs and stand up by driving the floor away.',
      mistakes: ['Letting the hips shoot up first, turning it into a stiff-leg pull.', 'Rounding the lower back under load.', 'Jerking the bar off the floor instead of taking the slack out.'] },
    { id: 'pull-up', name: 'Pull-Up', muscleGroup: 'Back', equipment: 'Bodyweight', difficulty: 'Intermediate',
      description: 'Hang at full stretch and pull until the chin clears the bar, driving the elbows down and back.',
      mistakes: ['Kipping when the goal is a strict rep.', 'Cutting the bottom range and never fully extending.'] },
    { id: 'barbell-row', name: 'Barbell Row', muscleGroup: 'Back', equipment: 'Barbell', difficulty: 'Intermediate',
      description: 'Hinge to roughly 45 degrees and row the bar to the lower ribs.',
      mistakes: ['Standing up progressively through the set.', 'Using so much momentum the lats stop working.'] },
    { id: 'back-squat', name: 'Barbell Back Squat', muscleGroup: 'Legs', equipment: 'Barbell', difficulty: 'Intermediate',
      description: 'Brace, break at the hips and knees together, and descend until the hip crease passes the knee.',
      mistakes: ['Knees caving inward out of the hole.', 'Rising hips first, which dumps the load onto the lower back.', 'Cutting depth as the weight climbs.'] },
    { id: 'romanian-deadlift', name: 'Romanian Deadlift', muscleGroup: 'Legs', equipment: 'Barbell', difficulty: 'Intermediate',
      description: 'Push the hips back with a near-straight leg until the hamstrings stretch, then drive the hips forward.',
      mistakes: ['Squatting the weight down instead of hinging.', 'Chasing depth past the point the back rounds.'] },
    { id: 'leg-press', name: 'Leg Press', muscleGroup: 'Legs', equipment: 'Machine', difficulty: 'Beginner',
      description: 'Lower the sled until the knees reach roughly 90 degrees, then press without locking out hard.',
      mistakes: ['Letting the lower back round off the pad at the bottom.', 'Snapping the knees into full lockout.'] },
    { id: 'hip-thrust', name: 'Barbell Hip Thrust', muscleGroup: 'Glutes', equipment: 'Barbell', difficulty: 'Beginner',
      description: 'With the shoulder blades on a bench, drive the hips to full extension and squeeze at the top.',
      mistakes: ['Hyperextending the lower back instead of finishing with the glutes.', 'Letting the chin drift up and the ribs flare.'] },
    { id: 'plank', name: 'Plank', muscleGroup: 'Abs', equipment: 'Bodyweight', difficulty: 'Beginner',
      description: 'Hold a straight line from heel to head on the forearms, ribs down and glutes braced.',
      mistakes: ['Letting the hips sag toward the floor.', 'Piking the hips up to make the hold easier.'] },
    { id: 'hanging-leg-raise', name: 'Hanging Leg Raise', muscleGroup: 'Abs', equipment: 'Bodyweight', difficulty: 'Advanced',
      description: 'From a dead hang, raise the legs to hip height or above without swinging.',
      mistakes: ['Swinging between reps and using momentum.', 'Only moving the hips while the lower back stays arched.'] },
    { id: 'rowing-machine', name: 'Rowing Machine', muscleGroup: 'Cardio', equipment: 'Rower', difficulty: 'Beginner',
      description: 'Drive with the legs, then swing the torso back, then pull the handle to the ribs. Reverse that order on the recovery.',
      mistakes: ['Pulling with the arms before the legs have driven.', 'Rounding the back at the catch.'] },
  ];
  const exerciseById = Object.fromEntries(exercises.map(e => [e.id, e]));

  function sets(weightKg, reps, n = 3) {
    return Array.from({ length: n }, (_, i) => ({ id: `set-${i}`, weightKg, reps, completed: false }));
  }

  /* ---- Workouts (SampleData.pushDay/pullDay/legDay) ------------------- */
  const workouts = [
    { id: 'push-day', name: 'Push Day', difficulty: 'Intermediate', estimatedMinutes: 52, scheduledDays: [1, 4],
      exercises: [
        { exerciseId: 'bench-press', position: 0, sets: sets(60, 8) },
        { exerciseId: 'incline-db-press', position: 1, sets: sets(24, 10) },
        { exerciseId: 'cable-fly', position: 2, sets: sets(15, 12) },
        { exerciseId: 'overhead-press', position: 3, sets: sets(40, 8) },
        { exerciseId: 'lateral-raise', position: 4, sets: sets(10, 14) },
        { exerciseId: 'triceps-pushdown', position: 5, sets: sets(30, 12) },
      ] },
    { id: 'pull-day', name: 'Pull Day', difficulty: 'Intermediate', estimatedMinutes: 48, scheduledDays: [2, 5],
      exercises: [
        { exerciseId: 'deadlift', position: 0, sets: sets(100, 5) },
        { exerciseId: 'pull-up', position: 1, sets: sets(0, 8) },
        { exerciseId: 'barbell-row', position: 2, sets: sets(60, 8) },
      ] },
    { id: 'leg-day', name: 'Leg Day', difficulty: 'Advanced', estimatedMinutes: 58, scheduledDays: [3, 6],
      exercises: [
        { exerciseId: 'back-squat', position: 0, sets: sets(90, 6) },
        { exerciseId: 'romanian-deadlift', position: 1, sets: sets(70, 8) },
        { exerciseId: 'leg-press', position: 2, sets: sets(140, 10) },
        { exerciseId: 'hip-thrust', position: 3, sets: sets(80, 10) },
      ] },
  ];
  const workoutById = Object.fromEntries(workouts.map(w => [w.id, w]));

  /* Home always leads with Push Day, mirroring HomeScreen.kt's own
     @Preview (todayWorkout = SampleData.pushDay) rather than gating the
     flagship card behind whatever weekday the prototype happens to load on. */
  const todayWorkoutId = 'push-day';

  function muscleGroupsFor(workout) {
    const seen = [];
    workout.exercises.forEach(we => {
      const g = exerciseById[we.exerciseId].muscleGroup;
      if (!seen.includes(g)) seen.push(g);
    });
    return seen;
  }

  /* ---- Weight entries: ~90 days ending at 78.4kg, cutting ~0.6kg/week - */
  const weightEntries = (() => {
    const kgPerDay = 0.6 / 7;
    const out = [];
    for (let d = 89; d >= 0; d -= 1) {
      const trend = 78.4 + d * kgPerDay;
      const wobble = Math.sin(d * 0.7) * 0.12 + (rng() * 0.12 - 0.06);
      out.push({ date: daysAgo(d), weightKg: Math.round((trend + wobble) * 10) / 10 });
    }
    return out;
  })();
  const currentWeightKg = weightEntries[weightEntries.length - 1].weightKg;
  function weeklyDeltaKg() {
    const latest = weightEntries[weightEntries.length - 1];
    const idx = weightEntries.length - 1 - 7;
    if (idx < 0) return null;
    return Math.round((latest.weightKg - weightEntries[idx].weightKg) * 10) / 10;
  }

  /* ---- Meals (SampleData.meals) --------------------------------------- */
  const meals = [
    { id: 'meal-1', name: 'Breakfast', items: [
      { name: 'Rolled oats', grams: 80, cal: 389, protein: 16.9, carbs: 66.3, fat: 6.9 },
      { name: 'Whole milk', grams: 200, cal: 61, protein: 3.2, carbs: 4.8, fat: 3.3 },
      { name: 'Banana', grams: 120, cal: 89, protein: 1.1, carbs: 22.8, fat: 0.3 },
    ] },
    { id: 'meal-2', name: 'Lunch', items: [
      { name: 'Chicken breast', grams: 200, cal: 165, protein: 31.0, carbs: 0.0, fat: 3.6 },
      { name: 'White rice', grams: 180, cal: 130, protein: 2.7, carbs: 28.2, fat: 0.3 },
      { name: 'Broccoli', grams: 100, cal: 34, protein: 2.8, carbs: 6.6, fat: 0.4 },
    ] },
  ];
  function mealMacros(meal) {
    return meal.items.reduce((acc, it) => {
      const f = it.grams / 100;
      acc.cal += it.cal * f; acc.protein += it.protein * f; acc.carbs += it.carbs * f; acc.fat += it.fat * f;
      return acc;
    }, { cal: 0, protein: 0, carbs: 0, fat: 0 });
  }
  const nutritionTarget = { cal: 2400, protein: 180, carbs: 240, fat: 70 };

  /* ---- Motivation quotes (SampleData.motivationQuotes) ------------------ */
  const motivationQuotes = [
    'Every rep counts.\nEvery set matters.',
    'Show up.\nGet stronger.',
    'Small wins.\nBig results.',
    'Discipline beats\nmotivation.',
    'The work you skip\nis the progress you lose.',
    'Trust the process.\nCount the reps.',
    'Make today count.\nNothing else does.',
  ];
  function quoteForToday() {
    const epochDay = Math.floor(Date.now() / REPS.utils.DAY_MS);
    return motivationQuotes[epochDay % motivationQuotes.length];
  }

  /* ======================================================================
     NEW — authored content for the Progress screen. Nothing below has a
     Compose source; it is composed strictly from the vocabulary above.
     ====================================================================== */

  const streak = {
    current: user.streakCount,
    longest: user.longestStreak,
    // Mon..Sun, derived from the *actual* scheduledDays on the three sample
    // workouts (which between them cover Mon-Sat, leaving only Sunday free) —
    // matching Streak.kt's own definition: a streak counts consecutive
    // *scheduled* days honoured, so a planned rest day never breaks it.
    thisWeek: (() => {
      const scheduledWeekdays = new Set(workouts.flatMap(w => w.scheduledDays));
      const todayMondayFirst = (new Date().getDay() + 6) % 7; // Mon=0..Sun=6
      return Array.from({ length: 7 }, (_, mondayFirstIdx) => {
        if (mondayFirstIdx > todayMondayFirst) return 'future';
        const jsWeekday = (mondayFirstIdx + 1) % 7; // Mon(0)->1 ... Sun(6)->0
        return scheduledWeekdays.has(jsWeekday) ? 'done' : 'rest';
      });
    })(),
  };
  streak.weeklyConsistency = streak.thisWeek.filter(s => s === 'done').length /
    streak.thisWeek.filter(s => s !== 'future').length;

  const analytics = {
    thisMonth: { workouts: 18, minutes: 870, volumeKg: 42300, calories: 6240 },
    lastMonth: { workouts: 15, minutes: 726, volumeKg: 35800, calories: 5115 },
  };

  const personalRecords = [
    { id: 'pr-newest', tag: 'Newest PR', exerciseId: 'bench-press', weightKg: 100, reps: 3, date: daysAgo(2), deltaKg: 5 },
    { id: 'pr-biggest', tag: 'Biggest jump', exerciseId: 'back-squat', weightKg: 130, reps: 5, date: daysAgo(9), deltaKg: 14, deltaPct: 12 },
    { id: 'pr-recent', tag: 'Milestone', exerciseId: 'deadlift', weightKg: 150, reps: 3, date: daysAgo(16), deltaKg: 10, note: '2x bodyweight' },
  ];

  const achievements = [
    { id: 'ach-1', name: 'First Rep', icon: 'icon-flag', unlocked: true, date: daysAgo(84) },
    { id: 'ach-2', name: '7-Day Streak', icon: 'icon-flame', unlocked: true, date: daysAgo(60) },
    { id: 'ach-3', name: 'PR Breaker', icon: 'icon-bolt', unlocked: true, date: daysAgo(40) },
    { id: 'ach-4', name: 'Iron Will · 50', icon: 'icon-trophy', unlocked: true, date: daysAgo(11) },
    { id: 'ach-5', name: 'Goal Getter', icon: 'icon-target', unlocked: true, date: daysAgo(5) },
    { id: 'ach-6', name: '30-Day Streak', icon: 'icon-medal', unlocked: false, progress: 0.4 },
    { id: 'ach-7', name: 'Century Club', icon: 'icon-trophy', unlocked: false, progress: 0.61 },
    { id: 'ach-8', name: 'Consistency King', icon: 'icon-check-circle', unlocked: false, progress: 0.75 },
  ];

  const workoutSessions = (() => {
    const rotation = ['push-day', 'pull-day', 'leg-day'];
    const names = { 'push-day': 'Push Day', 'pull-day': 'Pull Day', 'leg-day': 'Leg Day' };
    const out = [];
    const offsets = [0, 2, 4, 6, 9, 11, 13, 16];
    offsets.forEach((d, i) => {
      const wid = rotation[i % rotation.length];
      out.push({
        id: `sess-${i}`,
        workoutId: wid,
        name: names[wid],
        date: daysAgo(d),
        durationMin: 44 + Math.round(rng() * 16),
        volumeKg: 2100 + Math.round(rng() * 900),
        prsHit: (d === 2) ? ['Barbell Bench Press'] : (d === 9 ? ['Barbell Back Squat'] : (d === 16 ? ['Conventional Deadlift'] : [])),
      });
    });
    return out;
  })();

  /* Weekly strength series for the three "big lifts" — 12 weekly points,
     gently trending up with small realistic noise. */
  function buildStrengthSeries(startKg, weeklyGainKg) {
    const points = [];
    for (let w = 11; w >= 0; w -= 1) {
      const trend = startKg - w * weeklyGainKg;
      const noise = (rng() - 0.5) * (weeklyGainKg * 0.8);
      points.push({ weeksAgo: w, weightKg: Math.round((trend + noise) * 2) / 2 });
    }
    return points;
  }
  const strengthSeries = {
    'bench-press': { label: 'Bench Press', points: buildStrengthSeries(100, 1.7) },
    'back-squat': { label: 'Back Squat', points: buildStrengthSeries(130, 2.3) },
    'deadlift': { label: 'Deadlift', points: buildStrengthSeries(150, 2.5) },
  };

  const weeklyFrequency = (() => {
    const base = [3, 4, 4, 5, 3, 4, 5, 4];
    return base.map((count, i) => ({ label: `W${i + 1}`, count }));
  })();

  const muscleDistribution = [
    { group: 'Legs', pct: 24 },
    { group: 'Back', pct: 20 },
    { group: 'Chest', pct: 18 },
    { group: 'Shoulders', pct: 14 },
    { group: 'Arms', pct: 12 },
    { group: 'Glutes', pct: 6 },
    { group: 'Abs', pct: 4 },
    { group: 'Cardio', pct: 2 },
  ];

  const notifications = [
    { id: 'n1', icon: 'icon-flame', title: 'Push Day today', desc: 'Your session is scheduled. Keep the streak alive.', time: '7:00 AM', unread: true },
    { id: 'n2', icon: 'icon-bolt', title: 'New PR — Bench Press', desc: '100 kg × 3 beat your previous best.', time: '2 days ago', unread: true },
    { id: 'n3', icon: 'icon-trophy', title: 'Badge unlocked: Iron Will · 50', desc: 'You have logged 50 workouts. Nice work.', time: '11 days ago', unread: false },
  ];

  return {
    user, exercises, exerciseById, workouts, workoutById, todayWorkoutId, muscleGroupsFor,
    weightEntries, currentWeightKg, weeklyDeltaKg,
    meals, mealMacros, nutritionTarget,
    motivationQuotes, quoteForToday,
    streak, analytics, personalRecords, achievements, workoutSessions,
    strengthSeries, weeklyFrequency, muscleDistribution, notifications,
  };
})();
