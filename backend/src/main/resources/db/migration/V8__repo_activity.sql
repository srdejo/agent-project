-- Actividad reciente de los repos para la sección "Hecho" del daily (cambio daily-hecho-y-bloqueos).

-- Sin FK a project: el bloque activity puede traer un repo que todavía no está guardado; al leer se ignora.
CREATE TABLE repo_commit (
    project_id    TEXT NOT NULL,
    sha           TEXT NOT NULL,
    committed_at  TIMESTAMPTZ NOT NULL,
    subject       TEXT NOT NULL,
    PRIMARY KEY (project_id, sha)
);

CREATE INDEX idx_repo_commit_committed_at ON repo_commit(committed_at);

-- Historial: el sync solo inserta. Sobrevive al reemplazo completo del proyecto, por eso no cuelga de task.
CREATE TABLE task_closed (
    id          BIGSERIAL PRIMARY KEY,
    project_id  TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    source      VARCHAR(16) NOT NULL,
    scope       TEXT NOT NULL,
    number      TEXT,
    text        TEXT NOT NULL,
    closed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_task_closed_closed_at ON task_closed(closed_at);
