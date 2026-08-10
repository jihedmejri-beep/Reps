"""ExerciseDB provider scaffold.

ExerciseDB is a commercial, key-gated exercise API (distributed via
RapidAPI / exercisedb.dev). This project's first task was to fully
inspect and integrate the wger API; ExerciseDB's schema has deliberately
**not** been guessed at here, in keeping with the "never assume JSON
schemas" rule that governs this whole codebase.

This module exists to prove out the plug-in architecture end to end: it
registers correctly, participates in `run.py --providers`, and shares the
exact same `ExerciseProvider` contract as `providers/wger.py`. Wiring in
real data only requires:

1. Setting `REPS_EXERCISEDB_API_KEY` (and `REPS_EXERCISEDB_BASE_URL` if
   it differs from the default below).
2. Implementing `fetch_raw()` to page through the real API with
   `pipeline.downloader.PaginatedApiDownloader` (or a bespoke fetch loop,
   if ExerciseDB's pagination shape differs from wger's limit/offset
   style) and cache pages under `self.raw_dir`.
3. Implementing `normalize()` to map ExerciseDB's actual field names --
   inspected from live responses, never guessed -- onto
   `pipeline.models.RawExerciseRecord`.

Until then, both methods fail fast with an actionable error instead of
silently yielding no data or fabricating placeholder records.
"""

from __future__ import annotations

import os
from typing import Iterator

from pipeline.models import RawExerciseRecord
from providers.base import ExerciseProvider, register_provider

EXERCISEDB_BASE_URL = os.environ.get("REPS_EXERCISEDB_BASE_URL", "https://exercisedb.dev/api/v1")
EXERCISEDB_API_KEY = os.environ.get("REPS_EXERCISEDB_API_KEY")


@register_provider("exercisedb")
class ExerciseDbProvider(ExerciseProvider):
    """Plug-in scaffold for the ExerciseDB data source (not yet wired up)."""

    def fetch_raw(self) -> None:
        raise NotImplementedError(
            "ExerciseDbProvider.fetch_raw() is a scaffold. Inspect the live ExerciseDB API "
            "response shapes first (never assume field names), then implement pagination and "
            "raw-page caching under self.raw_dir, following providers/wger.py as a reference."
        )

    def normalize(self) -> Iterator[RawExerciseRecord]:
        raise NotImplementedError(
            "ExerciseDbProvider.normalize() is a scaffold. Implement it once fetch_raw() caches "
            "real ExerciseDB data, mapping its verified field names onto RawExerciseRecord."
        )
