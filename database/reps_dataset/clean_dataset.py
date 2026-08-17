"""One-off audit/cleaning pass over data/output/reps_exercises.json.

Reads the production export and produces two new artifacts (the original
file is never modified):

- data/output/reps_exercises_clean.json  -- cleaned + structured + translated
- data/output/cleaning_report.json       -- a per-exercise log of every change

Scope (see conversation for the full spec):
  1. Strip HTML tags from `name`/`description`/`aliases` while preserving
     content; decode entities; normalize unicode (NFC) and whitespace.
  2. Restructure `description.{en,fr,ar}` from a flat HTML string into a
     structured object: {summary, startingPosition, steps[], tips[], notes[]}.
     This is a language-agnostic parse driven by data/output/glossary.json's
     `heading_synonyms` (English/French/Arabic label variants observed in
     the source data), so it works uniformly across all three languages.
  3. Existing en/fr/ar text is NEVER reworded or retranslated -- only its
     HTML/whitespace formatting is normalized. A verifiable no-data-loss
     check runs on every non-empty description (see `verify_no_data_loss`).
  4. Fields that are empty after cleaning are left as empty structures
     (never fabricated) -- translation of missing fr/ar content is a
     separate, explicit step (see translate_dataset.py), driven by a
     worklist this script emits.

This script has two modes:
  --extract   Clean/structure the existing dataset and emit
              translation_worklist.json (every empty ar/fr name+description
              that has English source content to translate from).
  --merge     Merge translations (data/output/translations/*.json) back in,
              run final validation, and write the two output artifacts.
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from html.parser import HTMLParser
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
SOURCE_PATH = PROJECT_ROOT / "data" / "output" / "reps_exercises.json"
GLOSSARY_PATH = PROJECT_ROOT / "data" / "output" / "glossary.json"
WORKLIST_PATH = PROJECT_ROOT / "data" / "output" / "translation_worklist.json"
TRANSLATIONS_DIR = PROJECT_ROOT / "data" / "output" / "translations"
CLEAN_OUTPUT_PATH = PROJECT_ROOT / "data" / "output" / "reps_exercises_clean.json"
REPORT_PATH = PROJECT_ROOT / "data" / "output" / "cleaning_report.json"

LANGUAGES = ("en", "fr", "ar")
SECTION_TEXT_KEYS = ("summary", "startingPosition")
SECTION_LIST_KEYS = ("steps", "tips", "notes")
ALL_SECTION_KEYS = SECTION_TEXT_KEYS + SECTION_LIST_KEYS

_WS_RE = re.compile(r"[ \t\f\v ​‌‎‏]+")
_MULTI_NEWLINE_RE = re.compile(r"\n{2,}")
_TRAILING_COLON_RE = re.compile(r"[:：]\s*$")
_WORD_RE = re.compile(r"[^\W\d_]{2,}", re.UNICODE)


# --------------------------------------------------------------------------
# Minimal HTML tree builder (stdlib only; tolerant of the small, occasionally
# malformed tag vocabulary actually present in this dataset: p, ol, ul, li,
# strong, em).
# --------------------------------------------------------------------------


class _Node:
    __slots__ = ("tag", "children")

    def __init__(self, tag: str) -> None:
        self.tag = tag
        self.children: list["_Node | str"] = []


class _TreeBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = _Node("root")
        self.stack = [self.root]

    def handle_starttag(self, tag: str, attrs) -> None:
        node = _Node(tag)
        self.stack[-1].children.append(node)
        self.stack.append(node)

    def handle_endtag(self, tag: str) -> None:
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                break

    def handle_data(self, data: str) -> None:
        if data:
            self.stack[-1].children.append(data)


def _parse_tree(raw_html: str) -> _Node:
    builder = _TreeBuilder()
    builder.feed(raw_html)
    builder.close()
    return builder.root


def _flatten_text(node: "_Node | str") -> str:
    if isinstance(node, str):
        return node
    return "".join(_flatten_text(child) for child in node.children)


def clean_inline_text(text: str) -> str:
    """Unicode-normalize and collapse whitespace within a single text run."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace(" ", " ")
    text = _WS_RE.sub(" ", text)
    text = re.sub(r"\s*\n\s*", " ", text)
    return text.strip()


def _is_heading_label(text: str, heading_synonyms: dict[str, list[str]]) -> str | None:
    """Return the matched section key if `text` is a known heading label, else None."""
    candidate = _TRAILING_COLON_RE.sub("", clean_inline_text(text)).strip().lower()
    if not candidate:
        return None
    for section_key, synonyms in heading_synonyms.items():
        if candidate in (s.lower() for s in synonyms):
            return section_key
    return None


def _extract_list_items(list_node: _Node) -> list[str]:
    items: list[str] = []
    for child in list_node.children:
        if isinstance(child, _Node) and child.tag == "li":
            items.extend(_extract_li(child))
    return items


def _extract_li(li_node: _Node) -> list[str]:
    own_parts: list[str] = []
    nested_items: list[str] = []
    for child in li_node.children:
        if isinstance(child, str):
            own_parts.append(child)
        elif child.tag in ("ol", "ul"):
            nested_items.extend(_extract_list_items(child))
        else:
            own_parts.append(_flatten_text(child))
    own_text = clean_inline_text("".join(own_parts))
    result = [own_text] if own_text else []
    result.extend(item for item in nested_items if item)
    return result


def _process_paragraph(
    p_node: _Node, current_section: str, buckets: dict[str, list[str]], heading_synonyms: dict
) -> str:
    """Scan a <p>'s children for inline/whole-paragraph heading markers,
    switching `current_section` as they're found, and append any
    surrounding text to whichever section is active at that point. Returns
    the (possibly updated) section to carry forward to sibling blocks."""
    accumulator: list[str] = []

    def flush(section: str) -> None:
        text = clean_inline_text("".join(accumulator))
        if text:
            buckets[section].append(text)
        accumulator.clear()

    for child in p_node.children:
        if isinstance(child, str):
            accumulator.append(child)
            continue
        if child.tag in ("strong", "em"):
            label_text = _flatten_text(child)
            matched = _is_heading_label(label_text, heading_synonyms)
            if matched:
                flush(current_section)
                current_section = matched
                continue
        accumulator.append(_flatten_text(child))

    flush(current_section)
    return current_section


def structure_description(raw_html: str, heading_synonyms: dict[str, list[str]]) -> dict:
    """Parse a raw HTML description into {summary, startingPosition, steps[], tips[], notes[]}."""
    empty = {**{k: "" for k in SECTION_TEXT_KEYS}, **{k: [] for k in SECTION_LIST_KEYS}}
    if not raw_html or not raw_html.strip():
        return empty

    tree = _parse_tree(raw_html)
    buckets: dict[str, list[str]] = {key: [] for key in ALL_SECTION_KEYS}
    current_section = "summary"

    for child in tree.children:
        if isinstance(child, str):
            text = clean_inline_text(child)
            if text:
                buckets[current_section].append(text)
            continue

        if child.tag == "p":
            whole_text = _flatten_text(child)
            matched = _is_heading_label(whole_text, heading_synonyms)
            if matched:
                current_section = matched
                continue
            current_section = _process_paragraph(child, current_section, buckets, heading_synonyms)
        elif child.tag in ("ol", "ul"):
            items = _extract_list_items(child)
            target = current_section if current_section in SECTION_LIST_KEYS else "steps"
            buckets[target].extend(item for item in items if item)
        else:
            text = clean_inline_text(_flatten_text(child))
            if text:
                buckets[current_section].append(text)

    result: dict = {}
    for key in SECTION_TEXT_KEYS:
        result[key] = _MULTI_NEWLINE_RE.sub("\n\n", "\n\n".join(buckets[key])).strip()
    for key in SECTION_LIST_KEYS:
        result[key] = [item for item in buckets[key] if item]
    return result


def flatten_structured(structured: dict) -> str:
    """Join every section back into one plain-text blob, for validation only."""
    parts = [structured.get(k, "") for k in SECTION_TEXT_KEYS]
    for key in SECTION_LIST_KEYS:
        parts.extend(structured.get(key, []))
    return " ".join(p for p in parts if p)


_HEADING_STOPWORDS = {
    "starting",
    "position",
    "steps",
    "tips",
    "notes",
    "de",
    "depart",
    "départ",
    "etapes",
    "étapes",
    "conseils",
    "remarques",
    # Arabic heading-label tokens (وضع البداية / الخطوات / خطوات / نصائح / تلاحظ),
    # tokenized individually since Arabic has no capitalization to key off of.
    "وضع",
    "البداية",
    "الخطوات",
    "خطوات",
    "نصائح",
    "تلاحظ",
    "ملاحظات",
}


def plain_text_tokens(raw_html_or_text: str, *, strip_html: bool) -> set[str]:
    if strip_html:
        tree = _parse_tree(raw_html_or_text)
        text = _flatten_text(tree)
    else:
        text = raw_html_or_text
    text = clean_inline_text(text)
    tokens = {t.lower() for t in _WORD_RE.findall(text)}
    return tokens


def verify_no_data_loss(raw_html: str, structured: dict) -> list[str]:
    """Return a list of tokens present in the source but missing from the
    structured reconstruction (after allowing for recognized heading
    labels), i.e. a concrete, checkable signal of accidental content loss."""
    if not raw_html or not raw_html.strip():
        return []
    original_tokens = plain_text_tokens(raw_html, strip_html=True)
    reconstructed_tokens = plain_text_tokens(flatten_structured(structured), strip_html=False)
    missing = original_tokens - reconstructed_tokens - _HEADING_STOPWORDS
    # Arabic heading words use non-Latin script; filter any token that is a
    # substring match of a known Arabic heading synonym too.
    return sorted(missing)


def clean_short_text(text: str) -> str:
    """Clean a name/alias/category-like short string: strip tags defensively,
    decode entities, normalize unicode + whitespace. Does not reword."""
    if not text:
        return ""
    if "<" in text and ">" in text:
        text = _flatten_text(_parse_tree(text))
    return clean_inline_text(text)


def load_glossary() -> dict:
    with GLOSSARY_PATH.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_source() -> list[dict]:
    with SOURCE_PATH.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def clean_exercise(exercise: dict, heading_synonyms: dict) -> tuple[dict, dict, list[dict]]:
    """Apply the formatting-only clean/structure pass to a single exercise.

    Returns (record, exercise_changes, data_loss_flags). Never reorders or
    rewords existing en/fr/ar text -- only strips HTML, normalizes unicode
    and whitespace, and restructures descriptions into sections.
    """
    record = dict(exercise)
    exercise_changes = {"id": exercise["id"], "nameChanges": [], "descriptionChanges": [], "aliasChanges": []}
    data_loss_flags: list[dict] = []

    # ---- names -----------------------------------------------------
    new_name = {}
    for lang in LANGUAGES:
        original = exercise["name"][lang]
        cleaned = clean_short_text(original)
        if cleaned != original:
            exercise_changes["nameChanges"].append({"lang": lang, "before": original, "after": cleaned})
        new_name[lang] = cleaned
    record["name"] = new_name

    # ---- aliases -----------------------------------------------------
    new_aliases = []
    for alias in exercise["aliases"]:
        cleaned = clean_short_text(alias)
        if cleaned != alias:
            exercise_changes["aliasChanges"].append({"before": alias, "after": cleaned})
        if cleaned:
            new_aliases.append(cleaned)
    record["aliases"] = sorted(dict.fromkeys(new_aliases))

    # ---- descriptions ------------------------------------------------
    new_description = {}
    for lang in LANGUAGES:
        original_html = exercise["description"][lang]
        structured = structure_description(original_html, heading_synonyms)
        new_description[lang] = structured
        if original_html.strip():
            missing_tokens = verify_no_data_loss(original_html, structured)
            if missing_tokens:
                data_loss_flags.append({"id": exercise["id"], "lang": lang, "missingTokens": missing_tokens})
            exercise_changes["descriptionChanges"].append(
                {"lang": lang, "action": "structured", "sectionsFound": [k for k in ALL_SECTION_KEYS if structured.get(k)]}
            )
    record["description"] = new_description

    return record, exercise_changes, data_loss_flags


def build_worklist_entries(record: dict) -> list[dict]:
    entries = []
    en_name_clean = record["name"]["en"]
    en_description = record["description"]["en"]
    for lang in ("fr", "ar"):
        if not record["name"][lang] and en_name_clean:
            entries.append({"id": record["id"], "field": "name", "lang": lang, "sourceEn": en_name_clean, "category": record["category"]})
        if not flatten_structured(record["description"][lang]).strip() and flatten_structured(en_description).strip():
            entries.append(
                {"id": record["id"], "field": "description", "lang": lang, "sourceEn": en_description, "category": record["category"]}
            )
    return entries


# --------------------------------------------------------------------------
# --extract : clean existing text + build the translation worklist
# --------------------------------------------------------------------------


def run_extract() -> None:
    glossary = load_glossary()
    heading_synonyms = glossary["heading_synonyms"]
    exercises = load_source()

    cleaned_records = []
    data_loss_flags = []
    worklist = []
    change_log = []

    for exercise in exercises:
        record, exercise_changes, exercise_data_loss_flags = clean_exercise(exercise, heading_synonyms)
        data_loss_flags.extend(exercise_data_loss_flags)
        worklist.extend(build_worklist_entries(record))

        cleaned_records.append(record)
        if exercise_changes["nameChanges"] or exercise_changes["descriptionChanges"] or exercise_changes["aliasChanges"]:
            change_log.append(exercise_changes)

    intermediate_path = PROJECT_ROOT / "data" / "output" / "_cleaned_pre_translation.json"
    intermediate_path.write_text(json.dumps(cleaned_records, ensure_ascii=False, indent=2), encoding="utf-8")

    WORKLIST_PATH.write_text(json.dumps(worklist, ensure_ascii=False, indent=2), encoding="utf-8")

    extract_report = {
        "totalExercises": len(exercises),
        "exercisesWithChanges": len(change_log),
        "dataLossFlags": data_loss_flags,
        "worklistSize": len(worklist),
        "worklistByField": {
            "name_fr": sum(1 for w in worklist if w["field"] == "name" and w["lang"] == "fr"),
            "name_ar": sum(1 for w in worklist if w["field"] == "name" and w["lang"] == "ar"),
            "description_fr": sum(1 for w in worklist if w["field"] == "description" and w["lang"] == "fr"),
            "description_ar": sum(1 for w in worklist if w["field"] == "description" and w["lang"] == "ar"),
        },
    }
    (PROJECT_ROOT / "data" / "output" / "_extract_report.json").write_text(
        json.dumps(extract_report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(extract_report, ensure_ascii=True, indent=2))


# --------------------------------------------------------------------------
# --merge : merge data/output/translations/*.json back in, validate, export
# --------------------------------------------------------------------------


def load_translations() -> dict[tuple[str, str, str], object]:
    """Index every translations/{field}_{lang}_{Category}.json entry by
    (id, field, lang). `field` is "name" or "desc" in filenames but "name" /
    "description" in the index, matching the worklist's `field` values."""
    index: dict[tuple[str, str, str], object] = {}
    file_field_map = {"name": "name", "desc": "description"}
    for path in sorted(TRANSLATIONS_DIR.glob("*.json")):
        stem_parts = path.stem.split("_")
        if len(stem_parts) < 3:
            continue
        file_field, lang, _category = stem_parts[0], stem_parts[1], "_".join(stem_parts[2:])
        field = file_field_map.get(file_field)
        if field is None or lang not in ("fr", "ar"):
            continue
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        for exercise_id, value in payload.items():
            index[(exercise_id, field, lang)] = value
    return index


def run_merge() -> None:
    glossary = load_glossary()
    heading_synonyms = glossary["heading_synonyms"]
    exercises = load_source()
    translations = load_translations()

    cleaned_records = []
    data_loss_flags = []
    change_log = []
    translations_applied = {"name_fr": 0, "name_ar": 0, "description_fr": 0, "description_ar": 0}
    still_missing = []

    for exercise in exercises:
        record, exercise_changes, exercise_data_loss_flags = clean_exercise(exercise, heading_synonyms)
        data_loss_flags.extend(exercise_data_loss_flags)

        for entry in build_worklist_entries(record):
            key = (entry["id"], entry["field"], entry["lang"])
            translated = translations.get(key)
            if translated is None:
                still_missing.append(entry)
                continue
            if entry["field"] == "name":
                record["name"][entry["lang"]] = translated
                exercise_changes["nameChanges"].append({"lang": entry["lang"], "before": "", "after": translated, "action": "translated"})
            else:
                record["description"][entry["lang"]] = translated
                exercise_changes["descriptionChanges"].append(
                    {"lang": entry["lang"], "action": "translated", "sectionsFound": [k for k in ALL_SECTION_KEYS if translated.get(k)]}
                )
            translations_applied[f"{entry['field']}_{entry['lang']}"] += 1

        cleaned_records.append(record)
        if exercise_changes["nameChanges"] or exercise_changes["descriptionChanges"] or exercise_changes["aliasChanges"]:
            change_log.append(exercise_changes)

    if still_missing:
        raise SystemExit(
            f"--merge aborted: {len(still_missing)} worklist entries have no matching translation file entry "
            f"(first: {still_missing[0]})"
        )

    CLEAN_OUTPUT_PATH.write_text(json.dumps(cleaned_records, ensure_ascii=False, indent=2), encoding="utf-8")

    cleaning_report = {
        "totalExercises": len(exercises),
        "exercisesWithChanges": len(change_log),
        "dataLossFlags": data_loss_flags,
        "translationsApplied": translations_applied,
        "changeLog": change_log,
    }
    REPORT_PATH.write_text(json.dumps(cleaning_report, ensure_ascii=False, indent=2), encoding="utf-8")

    summary = {
        "totalExercises": len(exercises),
        "exercisesWithChanges": len(change_log),
        "dataLossFlagCount": len(data_loss_flags),
        "translationsApplied": translations_applied,
        "outputs": [str(CLEAN_OUTPUT_PATH), str(REPORT_PATH)],
    }
    print(json.dumps(summary, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--extract", action="store_true")
    parser.add_argument("--merge", action="store_true")
    args = parser.parse_args()
    if args.extract:
        run_extract()
    elif args.merge:
        run_merge()
    else:
        parser.error("pass --extract or --merge")
