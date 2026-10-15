CREATE TABLE sync_runs (
    id              BIGSERIAL PRIMARY KEY,
    ran_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_ids     JSONB NOT NULL DEFAULT '[]',
    updated_ids     JSONB NOT NULL DEFAULT '[]',
    unchanged_ids   JSONB NOT NULL DEFAULT '[]',
    rejected        JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_sync_runs_ran_at ON sync_runs(ran_at DESC);
