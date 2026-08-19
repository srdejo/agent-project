ALTER TABLE projects
    ADD COLUMN source_last_modified TIMESTAMPTZ;

ALTER TABLE projects
    DROP COLUMN last_sync_hash;
