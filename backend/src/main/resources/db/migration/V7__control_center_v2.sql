-- Control Center v2 (cambio OpenSpec control-center-fase-1): reestructuracion sin compatibilidad.
-- El historial anterior (project_snapshots) se descarta a proposito: media el % global del roadmap,
-- que deja de ser la metrica principal. Ver docs/DECISIONS.md.
DROP TABLE project_snapshots;
DROP TABLE sync_runs;
DROP TABLE projects;

CREATE TABLE project (
    id                       TEXT PRIMARY KEY,
    name                     TEXT NOT NULL,
    repo                     TEXT NOT NULL,
    alias                    TEXT,
    summary                  TEXT,
    state                    VARCHAR(16) NOT NULL,
    priority_rank            INTEGER,
    stack                    JSONB NOT NULL DEFAULT '[]',
    content_hash             VARCHAR(64),
    progress_rule_version    INTEGER NOT NULL DEFAULT 0,
    source_commit            TEXT,
    source_modified_at       TIMESTAMPTZ,
    missing_since            TIMESTAMPTZ,
    read_error               TEXT,
    read_error_at            TIMESTAMPTZ,
    roadmap_detected         BOOLEAN NOT NULL DEFAULT FALSE,
    roadmap_done             INTEGER NOT NULL DEFAULT 0,
    roadmap_total            INTEGER NOT NULL DEFAULT 0,
    objective_name           TEXT,
    objective_criteria       TEXT,
    objective_target_date    DATE,
    objective_scope          JSONB NOT NULL DEFAULT '[]',
    objective_done           INTEGER NOT NULL DEFAULT 0,
    objective_total          INTEGER NOT NULL DEFAULT 0,
    achieved_objectives      JSONB NOT NULL DEFAULT '[]',
    blockers                 JSONB NOT NULL DEFAULT '[]',
    openspec_detected        BOOLEAN NOT NULL DEFAULT FALSE,
    openspec_specs_count     INTEGER NOT NULL DEFAULT 0,
    openspec_archived_count  INTEGER NOT NULL DEFAULT 0,
    openspec_commands        JSONB NOT NULL DEFAULT '[]',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roadmap_stage (
    id            BIGSERIAL PRIMARY KEY,
    project_id    TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    ordinal       INTEGER NOT NULL,
    stage_key     TEXT,
    title         TEXT NOT NULL,
    marker        VARCHAR(16) NOT NULL,
    has_criteria  BOOLEAN NOT NULL,
    backlog       BOOLEAN NOT NULL
);

CREATE INDEX idx_roadmap_stage_project ON roadmap_stage(project_id, ordinal);

CREATE TABLE openspec_change (
    id              BIGSERIAL PRIMARY KEY,
    project_id      TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    archived        BOOLEAN NOT NULL,
    ordinal         INTEGER NOT NULL,
    metadata        TEXT,
    artifacts       JSONB,
    -- Que artefactos existen (proposal, design, tasks, metadata, rutas de specs) sin cargar su contenido.
    artifact_index  JSONB NOT NULL DEFAULT '[]',
    last_parsed_at  TIMESTAMPTZ,
    parse_error     TEXT,
    parse_error_at  TIMESTAMPTZ,
    UNIQUE (project_id, name)
);

CREATE TABLE task (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    source              VARCHAR(16) NOT NULL,
    stage_id            BIGINT REFERENCES roadmap_stage(id) ON DELETE CASCADE,
    change_id           BIGINT REFERENCES openspec_change(id) ON DELETE CASCADE,
    ordinal             INTEGER NOT NULL,
    number              TEXT,
    text                TEXT NOT NULL,
    section             TEXT,
    line                INTEGER NOT NULL,
    checked             BOOLEAN NOT NULL,
    cancelled           BOOLEAN NOT NULL,
    status              VARCHAR(16) NOT NULL,
    blocked_reason      TEXT,
    in_objective        BOOLEAN NOT NULL,
    held_by_blocker     TEXT
);

CREATE INDEX idx_task_project_status ON task(project_id, status);
CREATE INDEX idx_task_change ON task(change_id);

CREATE TABLE objective_progress_point (
    id              BIGSERIAL PRIMARY KEY,
    project_id      TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    objective_name  TEXT NOT NULL,
    done            INTEGER NOT NULL,
    total           INTEGER NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_objective_progress_project ON objective_progress_point(project_id, taken_at);

CREATE TABLE sync_run (
    id                 BIGSERIAL PRIMARY KEY,
    started_at         TIMESTAMPTZ NOT NULL,
    duration_ms        BIGINT NOT NULL,
    format_version     INTEGER,
    generator_version  TEXT,
    generated_at       TIMESTAMPTZ,
    file_error         TEXT,
    created_ids        JSONB NOT NULL DEFAULT '[]',
    updated_ids        JSONB NOT NULL DEFAULT '[]',
    unchanged_ids      JSONB NOT NULL DEFAULT '[]',
    missing_ids        JSONB NOT NULL DEFAULT '[]',
    rejected           JSONB NOT NULL DEFAULT '{}',
    read_errors        JSONB NOT NULL DEFAULT '{}',
    warnings           JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_sync_run_started_at ON sync_run(started_at DESC);
