-- schema.sql -- GENERATED FILE. Do not hand-edit.
-- Regenerate with: python db/migrate.py dump-schema
-- Source of truth: db/migrations/*.sql, applied in filename order.

-- ==== 0001_init.sql ====
-- 0001_init.sql
-- REPS backend data platform -- core normalized schema.
--
-- Design notes (see docs/database.md for the full explanation):
--   * Every entity has a stable primary key, created_at/updated_at
--     timestamps, and participates in `schema_migrations` version
--     tracking -- this is what lets the schema absorb future providers
--     (ExerciseDB, GymVisual, LiftManual, custom datasets) without ever
--     being redesigned.
--   * Lookup/taxonomy entities the current WGER-sourced data cannot
--     populate (tags, body_regions, movement_patterns, difficulties,
--     exercise_types, forces, mechanics) are created now as empty,
--     ready-to-populate tables; every FK to them on `exercises` is
--     nullable and left NULL rather than backfilled with invented data.
--   * `exercises.id`/`exercise_images.id`/`exercise_videos.id` reuse the
--     stable UUIDs already assigned upstream (wger's own uuid, or the
--     pipeline-generated uuid for custom providers) instead of minting a
--     new surrogate key -- this keeps the JSON export, the DB, and the
--     SQLite offline export referring to the exact same identity.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

CREATE TABLE schema_migrations (
    version     TEXT PRIMARY KEY,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- --------------------------------------------------------------------
-- Reference / lookup entities
-- --------------------------------------------------------------------

CREATE TABLE sources (
    id          SMALLSERIAL PRIMARY KEY,
    slug        TEXT NOT NULL UNIQUE,          -- 'wger', 'exercisedb', 'gymvisual', 'liftmanual', 'custom'
    name        TEXT NOT NULL,
    base_url    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO sources (slug, name, base_url) VALUES
    ('wger', 'WGER', 'https://wger.de/api/v2'),
    ('exercisedb', 'ExerciseDB', NULL),
    ('gymvisual', 'GymVisual', NULL),
    ('liftmanual', 'LiftManual', NULL),
    ('custom', 'Custom dataset', NULL);

CREATE TABLE licenses (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    url         TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (name)
);
CREATE TRIGGER trg_licenses_updated_at BEFORE UPDATE ON licenses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE categories (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE equipment (
    id           BIGSERIAL PRIMARY KEY,
    source_id    SMALLINT NOT NULL REFERENCES sources(id),
    external_id  TEXT NOT NULL,     -- source's own numeric/string id, as text
    name         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);
CREATE TRIGGER trg_equipment_updated_at BEFORE UPDATE ON equipment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE muscles (
    id                    BIGSERIAL PRIMARY KEY,
    source_id             SMALLINT NOT NULL REFERENCES sources(id),
    external_id           TEXT NOT NULL,
    name                  TEXT NOT NULL,
    name_en               TEXT,
    is_front              BOOLEAN,
    main_svg_local_path   TEXT,
    secondary_svg_local_path TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);
CREATE TRIGGER trg_muscles_updated_at BEFORE UPDATE ON muscles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Taxonomy tables not yet populated by any current provider. Present so
-- the schema never needs redesigning when a provider that *does* supply
-- this data (e.g. ExerciseDB's bodyPart/target, LiftManual's difficulty)
-- is wired in -- `exercises` FKs to these are nullable and stay NULL
-- until real data exists.

CREATE TABLE tags (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE body_regions (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE
);

CREATE TABLE movement_patterns (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE
);

CREATE TABLE difficulties (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    level       SMALLINT               -- 1 = easiest, ascending
);

CREATE TABLE exercise_types (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE   -- e.g. 'strength', 'cardio', 'stretch'
);

CREATE TABLE forces (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE   -- 'push', 'pull', 'static'
);

CREATE TABLE mechanics (
    id          SMALLSERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE   -- 'compound', 'isolation'
);

-- --------------------------------------------------------------------
-- Core exercise entity
-- --------------------------------------------------------------------

CREATE TABLE exercises (
    id                   UUID PRIMARY KEY,             -- reused stable uuid from the source provider
    source_id            SMALLINT NOT NULL REFERENCES sources(id),
    external_id          TEXT NOT NULL,                -- source's own exercise id, as text
    variation_group      UUID,
    category_id          SMALLINT REFERENCES categories(id),
    license_id           SMALLINT REFERENCES licenses(id),
    difficulty_id        SMALLINT REFERENCES difficulties(id),
    exercise_type_id     SMALLINT REFERENCES exercise_types(id),
    force_id             SMALLINT REFERENCES forces(id),
    mechanic_id          SMALLINT REFERENCES mechanics(id),
    movement_pattern_id  SMALLINT REFERENCES movement_patterns(id),
    body_region_id       SMALLINT REFERENCES body_regions(id),
    is_warmup            BOOLEAN,
    is_stretch           BOOLEAN,
    schema_version       INTEGER NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);
CREATE INDEX idx_exercises_category ON exercises(category_id);
CREATE INDEX idx_exercises_variation_group ON exercises(variation_group);
CREATE TRIGGER trg_exercises_updated_at BEFORE UPDATE ON exercises
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE exercise_translations (
    id                BIGSERIAL PRIMARY KEY,
    exercise_id       UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    language_code     VARCHAR(5) NOT NULL,             -- 'en' | 'fr' | 'ar'
    name              TEXT NOT NULL DEFAULT '',
    summary           TEXT NOT NULL DEFAULT '',
    starting_position TEXT NOT NULL DEFAULT '',
    steps             JSONB NOT NULL DEFAULT '[]'::jsonb,
    tips              JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes             JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exercise_id, language_code)
);
CREATE TRIGGER trg_exercise_translations_updated_at BEFORE UPDATE ON exercise_translations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE exercise_aliases (
    id            BIGSERIAL PRIMARY KEY,
    exercise_id   UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    alias         TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exercise_id, alias)
);
CREATE INDEX idx_exercise_aliases_exercise ON exercise_aliases(exercise_id);

CREATE TABLE exercise_equipment (
    exercise_id   UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    equipment_id  BIGINT NOT NULL REFERENCES equipment(id) ON DELETE RESTRICT,
    PRIMARY KEY (exercise_id, equipment_id)
);
CREATE INDEX idx_exercise_equipment_equipment ON exercise_equipment(equipment_id);

CREATE TABLE exercise_muscles (
    exercise_id   UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    muscle_id     BIGINT NOT NULL REFERENCES muscles(id) ON DELETE RESTRICT,
    role          VARCHAR(9) NOT NULL CHECK (role IN ('primary', 'secondary')),
    PRIMARY KEY (exercise_id, muscle_id, role)
);
CREATE INDEX idx_exercise_muscles_muscle ON exercise_muscles(muscle_id);

CREATE TABLE exercise_tags (
    exercise_id   UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    tag_id        BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (exercise_id, tag_id)
);

-- --------------------------------------------------------------------
-- Assets (Epic 1)
-- --------------------------------------------------------------------

CREATE TABLE exercise_images (
    id               UUID PRIMARY KEY,                 -- reused asset uuid
    exercise_id      UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    is_main          BOOLEAN NOT NULL DEFAULT false,
    url              TEXT NOT NULL,
    local_path       TEXT,
    width            INTEGER,
    height           INTEGER,
    size_bytes       BIGINT,
    checksum_sha256  CHAR(64),
    license_id       SMALLINT REFERENCES licenses(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_exercise_images_exercise ON exercise_images(exercise_id);
CREATE TRIGGER trg_exercise_images_updated_at BEFORE UPDATE ON exercise_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE exercise_videos (
    id               UUID PRIMARY KEY,
    exercise_id      UUID NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    url              TEXT NOT NULL,
    local_path       TEXT,
    width            INTEGER,
    height           INTEGER,
    duration_seconds NUMERIC,
    codec            TEXT,
    size_bytes       BIGINT,
    checksum_sha256  CHAR(64),
    license_id       SMALLINT REFERENCES licenses(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_exercise_videos_exercise ON exercise_videos(exercise_id);
CREATE TRIGGER trg_exercise_videos_updated_at BEFORE UPDATE ON exercise_videos
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE muscle_svg_assets (
    muscle_id   BIGINT NOT NULL REFERENCES muscles(id) ON DELETE CASCADE,
    variant     VARCHAR(9) NOT NULL CHECK (variant IN ('main', 'secondary')),
    local_path  TEXT NOT NULL,
    width       INTEGER,
    height      INTEGER,
    PRIMARY KEY (muscle_id, variant)
);

CREATE TABLE body_diagrams (
    side        VARCHAR(5) PRIMARY KEY CHECK (side IN ('front', 'back')),
    local_path  TEXT NOT NULL,
    width       INTEGER,
    height      INTEGER
);

INSERT INTO schema_migrations (version) VALUES ('0001_init');

COMMIT;

-- ==== 0002_search.sql ====
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
