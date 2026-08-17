"""Centralized logging configuration for the REPS pipeline.

Every pipeline stage and provider obtains its logger through
:func:`get_logger`, which guarantees a single, idempotently configured
logging setup: coloured/plain console output plus a rotating file handler
under ``data/logs/``.
"""

from __future__ import annotations

import logging
import sys
from logging.handlers import RotatingFileHandler

from config import LOGS_DIR, ensure_directories

_LOG_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)-28s | %(message)s"
_DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
_ROOT_LOGGER_NAME = "reps"
_CONFIGURED = False


class _LevelColorFormatter(logging.Formatter):
    """Adds ANSI colour to console output when the stream supports it."""

    _COLORS = {
        logging.DEBUG: "\033[36m",
        logging.INFO: "\033[32m",
        logging.WARNING: "\033[33m",
        logging.ERROR: "\033[31m",
        logging.CRITICAL: "\033[41m",
    }
    _RESET = "\033[0m"

    def __init__(self, use_color: bool) -> None:
        super().__init__(fmt=_LOG_FORMAT, datefmt=_DATE_FORMAT)
        self._use_color = use_color

    def format(self, record: logging.LogRecord) -> str:
        message = super().format(record)
        if not self._use_color:
            return message
        color = self._COLORS.get(record.levelno, "")
        return f"{color}{message}{self._RESET}" if color else message


def _stream_supports_color(stream: "sys.TextIO") -> bool:
    return hasattr(stream, "isatty") and stream.isatty()


def _configure_root() -> None:
    global _CONFIGURED
    if _CONFIGURED:
        return

    ensure_directories()

    root = logging.getLogger(_ROOT_LOGGER_NAME)
    root.setLevel(logging.DEBUG)
    root.propagate = False

    console_handler = logging.StreamHandler(stream=sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(_LevelColorFormatter(_stream_supports_color(sys.stdout)))
    root.addHandler(console_handler)

    file_handler = RotatingFileHandler(
        LOGS_DIR / "pipeline.log",
        maxBytes=10 * 1024 * 1024,
        backupCount=5,
        encoding="utf-8",
    )
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter(fmt=_LOG_FORMAT, datefmt=_DATE_FORMAT))
    root.addHandler(file_handler)

    _CONFIGURED = True


def get_logger(name: str) -> logging.Logger:
    """Return a namespaced logger (e.g. ``reps.pipeline.downloader``)."""
    _configure_root()
    qualified_name = name if name.startswith(_ROOT_LOGGER_NAME) else f"{_ROOT_LOGGER_NAME}.{name}"
    return logging.getLogger(qualified_name)


def set_verbosity(level: int) -> None:
    """Adjust the console handler's verbosity (file handler stays at DEBUG)."""
    _configure_root()
    root = logging.getLogger(_ROOT_LOGGER_NAME)
    for handler in root.handlers:
        if isinstance(handler, logging.StreamHandler) and not isinstance(handler, RotatingFileHandler):
            handler.setLevel(level)
