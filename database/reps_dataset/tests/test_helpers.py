"""Tests for pipeline/helpers.py::resolve_asset_url (cloud asset URL
cutover point -- see docs/assets.md -> "Cloud migration")."""

from __future__ import annotations

from pipeline.helpers import resolve_asset_url


def test_resolve_asset_url_returns_local_path_when_no_base_url():
    assert resolve_asset_url("assets/exercises/1000/main.png", None) == "assets/exercises/1000/main.png"


def test_resolve_asset_url_joins_base_url_when_configured():
    assert (
        resolve_asset_url("assets/exercises/1000/main.png", "https://cdn.example.com")
        == "https://cdn.example.com/assets/exercises/1000/main.png"
    )


def test_resolve_asset_url_handles_trailing_and_leading_slashes():
    assert (
        resolve_asset_url("/assets/exercises/1000/main.png", "https://cdn.example.com/")
        == "https://cdn.example.com/assets/exercises/1000/main.png"
    )


def test_resolve_asset_url_returns_none_for_missing_local_path():
    assert resolve_asset_url(None, "https://cdn.example.com") is None
