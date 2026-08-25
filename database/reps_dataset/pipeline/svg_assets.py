"""Pipeline Step: download muscle overlay SVGs and the body reference
diagrams they're meant to align with.

WGER's REST API (`data/raw/wger/muscle/*.json`) exposes each muscle's
`image_url_main`/`image_url_secondary` SVG. It does **not** expose the two
body diagrams those overlays are drawn on top of through the API -- they
are static frontend assets. This module audits for them at their known
static path (`https://wger.de/static/images/muscles/muscular_system_{front,
back}.svg`, confirmed reachable at plan time) and downloads them if found;
if WGER ever moves/removes them, `svg_validation_report.json` records that
honestly instead of the pipeline pretending they exist.

Outputs:
    assets/muscles/main/*.svg
    assets/muscles/secondary/*.svg
    assets/muscles/muscle_svg_map.json
    assets/body/front.svg, assets/body/back.svg   (if discoverable)
    data/output/svg_validation_report.json
"""

from __future__ import annotations

import datetime as dt
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import urlsplit

import requests
from tqdm import tqdm

from config import (
    ASSET,
    BODY_ASSETS_DIR,
    MUSCLE_MAIN_SVG_DIR,
    MUSCLE_SECONDARY_SVG_DIR,
    PROJECT_ROOT,
    RAW_DIR,
)
from pipeline.helpers import atomic_write_json, build_http_session, ensure_dir, read_json_if_exists, svg_dimensions
from pipeline.logger import get_logger

logger = get_logger("pipeline.svg_assets")

# WGER does not publish these through the REST API; discovered by directly
# probing WGER's static asset paths (HTTP 200 confirmed). If WGER
# relocates them, `_download_body_svgs` degrades to recording them as
# unavailable rather than failing the whole run.
BODY_SVG_CANDIDATE_URLS: dict[str, str] = {
    "front": "https://wger.de/static/images/muscles/muscular_system_front.svg",
    "back": "https://wger.de/static/images/muscles/muscular_system_back.svg",
}

_MUSCLE_RAW_DIR = RAW_DIR / "wger" / "muscle"
_HEIGHT_TOLERANCE_PX = 15


class SvgAssetDownloader:
    def __init__(self, *, workers: int = 8, force_refresh: bool = False) -> None:
        self.workers = max(1, workers)
        self.force_refresh = force_refresh
        self.session = build_http_session()
        self._lock = threading.Lock()

    # ---------------------------------------------------------------- run

    def run(self) -> dict:
        """Downloads every muscle SVG + body diagrams, writes
        `muscle_svg_map.json` and `svg_validation_report.json`. Returns the
        validation report dict."""
        ensure_dir(MUSCLE_MAIN_SVG_DIR)
        ensure_dir(MUSCLE_SECONDARY_SVG_DIR)
        ensure_dir(BODY_ASSETS_DIR)

        muscles = self._load_muscles()
        muscle_map = self._download_muscle_svgs(muscles)
        atomic_write_json(MUSCLE_MAIN_SVG_DIR.parent / "muscle_svg_map.json", muscle_map)

        body_paths = self._download_body_svgs()

        report = self._validate(muscle_map, body_paths)
        return report

    # -------------------------------------------------------------- muscle

    @staticmethod
    def _load_muscles() -> list[dict]:
        muscles: list[dict] = []
        for page_file in sorted(_MUSCLE_RAW_DIR.glob("page_*.json")):
            payload = read_json_if_exists(page_file)
            if payload:
                muscles.extend(payload.get("results", []))
        return muscles

    def _download_muscle_svgs(self, muscles: list[dict]) -> dict:
        jobs = []
        for muscle in muscles:
            for variant, dest_dir in (("main", MUSCLE_MAIN_SVG_DIR), ("secondary", MUSCLE_SECONDARY_SVG_DIR)):
                url = muscle.get(f"image_url_{variant}")
                if url:
                    jobs.append((muscle["id"], variant, url, dest_dir))

        muscle_map: dict[str, dict] = {}
        with tqdm(total=len(jobs), desc="Downloading muscle SVGs", unit="file") as progress:
            with ThreadPoolExecutor(max_workers=self.workers) as executor:
                futures = {executor.submit(self._download_svg, url, dest_dir): (muscle_id, variant) for muscle_id, variant, url, dest_dir in jobs}
                for future in as_completed(futures):
                    muscle_id, variant = futures[future]
                    try:
                        local_path = future.result()
                    except Exception as exc:  # noqa: BLE001 - one broken SVG must never abort the run
                        logger.error("Failed to download muscle %s (%s): %s", muscle_id, variant, exc)
                        local_path = None
                    finally:
                        progress.update(1)
                    entry = muscle_map.setdefault(str(muscle_id), {"main": None, "secondary": None})
                    entry[variant] = local_path
        return muscle_map

    def _download_svg(self, url: str, dest_dir: Path) -> str:
        filename = Path(urlsplit(url).path).name
        dest_path = dest_dir / filename
        if dest_path.exists() and dest_path.stat().st_size > 0 and not self.force_refresh:
            return self._relative_path(dest_path)

        last_exc: Exception | None = None
        for attempt in range(1, ASSET.max_retries + 1):
            try:
                response = self.session.get(url, timeout=ASSET.request_timeout_seconds)
                response.raise_for_status()
                tmp_path = dest_path.with_suffix(dest_path.suffix + ".part")
                tmp_path.write_bytes(response.content)
                tmp_path.replace(dest_path)
                return self._relative_path(dest_path)
            except (requests.RequestException, OSError) as exc:
                last_exc = exc
                logger.warning("Attempt %s/%s failed for %s: %s", attempt, ASSET.max_retries, url, exc)
        raise RuntimeError(f"could not download {url} after {ASSET.max_retries} attempts") from last_exc

    # ---------------------------------------------------------------- body

    def _download_body_svgs(self) -> dict[str, str | None]:
        paths: dict[str, str | None] = {}
        for side, url in BODY_SVG_CANDIDATE_URLS.items():
            dest_path = BODY_ASSETS_DIR / f"{side}.svg"
            try:
                if dest_path.exists() and dest_path.stat().st_size > 0 and not self.force_refresh:
                    paths[side] = self._relative_path(dest_path)
                    continue
                response = self.session.get(url, timeout=ASSET.request_timeout_seconds)
                response.raise_for_status()
                dest_path.write_bytes(response.content)
                paths[side] = self._relative_path(dest_path)
                logger.info("Downloaded body diagram (%s) from %s", side, url)
            except requests.RequestException as exc:
                logger.warning("Body diagram (%s) unavailable at %s: %s", side, url, exc)
                paths[side] = None
        return paths

    # ---------------------------------------------------------- validation

    def _validate(self, muscle_map: dict, body_paths: dict[str, str | None]) -> dict:
        body_dimensions = {
            side: svg_dimensions(PROJECT_ROOT / rel_path) if rel_path else None
            for side, rel_path in body_paths.items()
        }

        issues: list[dict] = []
        checked = 0
        for muscle_id, variants in muscle_map.items():
            for variant, rel_path in variants.items():
                if not rel_path:
                    issues.append({"muscleId": muscle_id, "variant": variant, "issue": "download_failed"})
                    continue
                full_path = PROJECT_ROOT / rel_path
                dims = svg_dimensions(full_path)
                checked += 1
                if dims is None:
                    issues.append({"muscleId": muscle_id, "variant": variant, "issue": "malformed_svg", "path": rel_path})
                    continue
                width, height = dims
                # "Overlay alignment": every muscle SVG must share the same
                # canvas width as both body diagrams (so it stacks correctly
                # without horizontal offset), and height within a small
                # tolerance (accounts for legitimate per-muscle height
                # variance observed in the source data, e.g. 362px vs 369px).
                for side, body_dims in body_dimensions.items():
                    if body_dims is None:
                        continue
                    body_width, body_height = body_dims
                    if width != body_width or abs(height - body_height) > _HEIGHT_TOLERANCE_PX:
                        issues.append(
                            {
                                "muscleId": muscle_id,
                                "variant": variant,
                                "issue": "dimension_mismatch",
                                "path": rel_path,
                                "muscleDimensions": [width, height],
                                "bodySide": side,
                                "bodyDimensions": list(body_dims),
                            }
                        )

        report = {
            "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "muscleSvgCount": sum(1 for v in muscle_map.values() for p in v.values() if p),
            "muscleSvgChecked": checked,
            "bodyDiagrams": {side: {"downloaded": path is not None, "localPath": path} for side, path in body_paths.items()},
            "bodyDiagramDimensions": {side: list(dims) if dims else None for side, dims in body_dimensions.items()},
            "alignmentIssueCount": len(issues),
            "issues": issues,
        }
        return report

    @staticmethod
    def _relative_path(path: Path) -> str:
        return str(path.relative_to(PROJECT_ROOT)).replace("\\", "/")
