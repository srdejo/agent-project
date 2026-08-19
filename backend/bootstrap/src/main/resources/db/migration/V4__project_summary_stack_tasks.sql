ALTER TABLE projects
    ADD COLUMN summary TEXT,
    ADD COLUMN stack   JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN tasks   JSONB NOT NULL DEFAULT '[]';

ALTER TABLE projects
    DROP COLUMN completed,
    DROP COLUMN next_tasks,
    DROP COLUMN blocked;
