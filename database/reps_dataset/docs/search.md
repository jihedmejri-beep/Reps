# Search architecture (Epic 4)

## What's supported

Search by exercise name, aliases, equipment, primary/secondary muscle,
category, tags (schema-ready, unpopulated today), difficulty/exercise
type/movement pattern/body region (schema-ready, unpopulated today), across
English, French and Arabic — full-text, fuzzy (typo-tolerant), and
accent/diacritic-insensitive.

## How it works

`exercise_translations` (one row per exercise per language) carries three
search-specific columns, added by `db/migrations/0002_search.sql`:

- `search_normalized` — the exercise's own name, lowercased and
  accent/diacritic-stripped (see "Normalization" below).
- `keywords` (`text[]`) — aliases + equipment names + muscle names + category
  name for that exercise, built by `pipeline/search_indexer.py`.
- `search_vector` (`tsvector`, GIN-indexed) — maintained automatically by a
  `BEFORE INSERT OR UPDATE` trigger from the two columns above, weighted
  (`name` = 'A', `keywords` = 'B') so name matches rank above keyword-only
  matches.

Fuzzy matching uses `pg_trgm` (trigram similarity) on `search_normalized`
and on `exercise_aliases.alias`, both GIN-indexed
(`gin_trgm_ops`) — this is what makes `similarity(search_normalized, ...)`
and `search_normalized % 'query'` fast at 828+ rows and beyond.

## Normalization, and why Arabic is handled differently

- English/French: `normalize_latin()` = `lower(unaccent(text))`, using
  Postgres's `unaccent` extension (strips Latin diacritics — `é` → `e`).
- Arabic: **Postgres ships no Arabic stemming dictionary.** `normalize_arabic()`
  strips tashkeel (diacritics), tatweel, and normalizes common letter
  variants (alef forms → bare alef, teh marbuta → heh, alef maksura → yeh),
  and Arabic tsvectors use the `simple` config (tokenization only, no
  morphological stemming — plurals/verb forms are not automatically related
  to their root). Combined with the trigram index, this gives real
  typo/diacritic-tolerant matching, but it is **not** true Arabic
  morphological search. If that's needed later, the standard path is a
  Postgres extension like `pg_similarity` with an Arabic-aware dictionary,
  or an external search engine (Elasticsearch/Meilisearch/Typesense all
  ship real Arabic analyzers) sitting in front of the same normalized data.

Verified live (see `ENGINEERING_REPORT.md` for the exact queries/output):
English `plainto_tsquery('push up')` correctly ranks `Push-Up` variants;
French trigram search for the unaccented `developpe` correctly finds
`Développé couché/Arnold/Larsen`; Arabic substring search for `عضلات`
(muscles) without diacritics correctly matches diacritic-free names.

## Query patterns (for the backend API layer)

```sql
-- Full-text (ranked)
SELECT exercise_id, name, ts_rank(search_vector, query) AS rank
FROM exercise_translations, plainto_tsquery('english', $1) query
WHERE language_code = 'en' AND search_vector @@ query
ORDER BY rank DESC LIMIT 20;

-- Fuzzy / typo-tolerant
SELECT exercise_id, name, similarity(search_normalized, normalize_latin($1)) AS sim
FROM exercise_translations
WHERE language_code = $2 AND search_normalized % normalize_latin($1)
ORDER BY sim DESC LIMIT 20;
```

For Arabic, substitute `normalize_arabic($1)` for `normalize_latin($1)` and
use the `simple` tsvector config in the full-text form.

## Offline (Android)

`pipeline/search_indexer.py` also writes `data/output/
exercise_search_index.json` (per exercise, per language: name + keywords) —
a lightweight index any client can consume directly. `db/sqlite_export.py`
mirrors the same name/keywords into a SQLite **FTS5** virtual table
(`exercise_fts`), so basic search works with zero network connectivity;
fuzzy/accent-insensitive matching is a Postgres-only capability today (FTS5
has its own trigram-like extensions, e.g. `spellfix1`, if that's needed
offline later — not implemented in the current SQLite export).

## Regenerating the index

```
python pipeline/search_indexer.py   # updates Postgres, writes exercise_search_index.json
python db/sqlite_export.py          # mirrors into the offline FTS5 cache
```
