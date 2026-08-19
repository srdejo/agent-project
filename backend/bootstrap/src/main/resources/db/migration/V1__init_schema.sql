CREATE TABLE projects (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    repo            TEXT NOT NULL,
    stage           TEXT,
    status          TEXT NOT NULL,
    progress        INTEGER NOT NULL,
    updated_label   TEXT,
    commit_sha      TEXT,
    verify_status   TEXT,
    completed       JSONB NOT NULL DEFAULT '[]',
    next_tasks      JSONB NOT NULL DEFAULT '[]',
    blocked         JSONB NOT NULL DEFAULT '[]',
    checks          JSONB NOT NULL DEFAULT '[]',
    events          JSONB NOT NULL DEFAULT '[]',
    last_sync_hash  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project_snapshots (
    id          BIGSERIAL PRIMARY KEY,
    project_id  TEXT NOT NULL REFERENCES projects(id),
    progress    INTEGER NOT NULL,
    stage       TEXT,
    status      TEXT NOT NULL,
    taken_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_snapshots_project_id ON project_snapshots(project_id, taken_at);
