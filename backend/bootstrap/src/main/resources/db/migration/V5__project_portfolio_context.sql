ALTER TABLE projects
    ADD COLUMN alias         VARCHAR(120),
    ADD COLUMN priority      VARCHAR(16),
    ADD COLUMN open_question TEXT;
