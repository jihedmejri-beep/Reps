"""Provider plug-in contract.

A "provider" is anything that can supply exercise data to the REPS
pipeline: the wger REST API today, and ExerciseDB / GymVisual / LiftManual
/ locally curated datasets in the future. The pipeline core
(`pipeline/cleaner.py`, `merger.py`, `media_downloader.py`,
`validator.py`, `exporter.py`) never talks to a provider's native API or
file format directly — it only consumes `RawExerciseRecord` objects
(`pipeline/models.py`), which is the single contract every provider must
satisfy.

Adding a new data source therefore never requires touching the pipeline:
create a new module in `providers/`, subclass `ExerciseProvider`, register
it with `@register_provider(...)`, and enable it via `config.py` or the
`--providers` CLI flag on `run.py`.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path
from typing import Iterator

from config import CONCURRENCY, RAW_DIR
from pipeline.models import RawExerciseRecord

_PROVIDER_REGISTRY: dict[str, type["ExerciseProvider"]] = {}


def register_provider(name: str):
    """Class decorator registering a provider under a stable string key."""

    def decorator(provider_cls: type["ExerciseProvider"]) -> type["ExerciseProvider"]:
        if name in _PROVIDER_REGISTRY and _PROVIDER_REGISTRY[name] is not provider_cls:
            raise ValueError(f"A provider is already registered under the name {name!r}")
        provider_cls.provider_name = name
        _PROVIDER_REGISTRY[name] = provider_cls
        return provider_cls

    return decorator


def get_provider_class(name: str) -> type["ExerciseProvider"]:
    try:
        return _PROVIDER_REGISTRY[name]
    except KeyError as exc:
        available = ", ".join(sorted(_PROVIDER_REGISTRY)) or "<none registered>"
        raise KeyError(f"Unknown provider {name!r}. Available providers: {available}") from exc


def available_providers() -> list[str]:
    return sorted(_PROVIDER_REGISTRY)


class ExerciseProvider(ABC):
    """Base class every exercise data provider must implement."""

    #: Set automatically by `@register_provider`.
    provider_name: str = ""

    def __init__(self, *, force_refresh: bool = False, download_workers: int = CONCURRENCY.download_workers) -> None:
        self.force_refresh = force_refresh
        self.download_workers = download_workers

    @property
    def raw_dir(self) -> Path:
        """Directory this provider's raw downloads are stored under."""
        directory = RAW_DIR / self.provider_name
        directory.mkdir(parents=True, exist_ok=True)
        return directory

    @abstractmethod
    def fetch_raw(self) -> None:
        """Download every dataset this provider needs into `self.raw_dir`.

        Implementations must be resumable and idempotent: re-running
        `python run.py` must skip already-downloaded, complete data unless
        `force_refresh` is set.
        """

    @abstractmethod
    def normalize(self) -> Iterator[RawExerciseRecord]:
        """Yield every exercise as a `RawExerciseRecord`.

        Must only read from `self.raw_dir` (data written by `fetch_raw`),
        never perform network I/O itself, so cleaning/merging can be
        re-run offline against cached raw data.
        """

    def __repr__(self) -> str:  # pragma: no cover - trivial
        return f"<{type(self).__name__} provider={self.provider_name!r}>"
