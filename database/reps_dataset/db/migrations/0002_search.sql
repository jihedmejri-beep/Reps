-- 0002_search.sql
-- Multilingual search layer (Epic 4): full-text search, fuzzy matching,
-- and accent/diacritic-insensitive search across en/fr/ar.
--
-- Honesty note (see docs/search.md): Postgres ships no Arabic stemming
-- dictionary, so Arabic uses the `simple` tsvector config (tokenization
-- only, no morphological stemming) plus `normalize_arabic()` below and
-- `pg_trgm` fuzzy matching to compensate -- this is not full Arabic
-- morphological search, and the docs say so explicitly.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Strips Arabic diacritics (tashkeel), tatweel, and normalizes common
-- letter variants (alef forms -> bare alef, teh marbuta -> heh, alef
-- maksura -> yeh) so "مقاومة", "مُقَاوَمَة" and "مقاومه" all match.
CREATE OR REPLACE FUNCTION normalize_arabic(input TEXT) RETURNS TEXT AS $$
    SELECT translate(
        regexp_replace(coalesce(input, ''), '[ؐ-ًؚ-ٰٟۖ-ۜ۟-۪ۨ-ۭـ]', '', 'g'),
        'أإآاىة',
        'ااااية'
    );
$$ LANGUAGE sql IMMUTABLE;

-- unaccent's default "unaccent" text search dictionary already strips
-- Latin diacritics (é -> e, etc.) for French/English; wrap it so search
-- normalization has one call site regardless of language.
CREATE OR REPLACE FUNCTION normalize_latin(input TEXT) RETURNS TEXT AS $$
    SELECT lower(unaccent(coalesce(input, '')));
$$ LANGUAGE sql IMMUTABLE;

ALTER TABLE exercise_translations
    ADD COLUMN search_normalized TEXT,
    ADD COLUMN search_vector tsvector,
    ADD COLUMN keywords TEXT[] NOT NULL DEFAULT '{}';

-- Populated by `pipeline/search_indexer.py` (keywords are language-aware:
-- name + aliases + equipment + muscles + category + tags, normalized).
-- The tsvector itself is maintained by this trigger so it can never drift
-- from `search_normalized`/`keywords`.
CREATE OR REPLACE FUNCTION exercise_translations_search_vector_update() RETURNS TRIGGER AS $$
DECLARE
    config regconfig;
BEGIN
    config := CASE NEW.language_code
        WHEN 'en' THEN 'english'::regconfig
        WHEN 'fr' THEN 'french'::regconfig
        ELSE 'simple'::regconfig  -- 'ar' and anything else: no stemming dictionary available
    END;
    NEW.search_vector :=
        setweight(to_tsvector(config, coalesce(NEW.search_normalized, '')), 'A') ||
        setweight(to_tsvector(config, array_to_string(NEW.keywords, ' ')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_exercise_translations_search_vector
    BEFORE INSERT OR UPDATE OF search_normalized, keywords ON exercise_translations
    FOR EACH ROW EXECUTE FUNCTION exercise_translations_search_vector_update();

CREATE INDEX idx_exercise_translations_search_vector ON exercise_translations USING GIN (search_vector);
CREATE INDEX idx_exercise_translations_name_trgm ON exercise_translations USING GIN (search_normalized gin_trgm_ops);
CREATE INDEX idx_exercise_aliases_alias_trgm ON exercise_aliases USING GIN (alias gin_trgm_ops);

INSERT INTO schema_migrations (version) VALUES ('0002_search');

COMMIT;
