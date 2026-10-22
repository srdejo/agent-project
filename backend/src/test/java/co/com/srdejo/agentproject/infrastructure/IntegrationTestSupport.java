package co.com.srdejo.agentproject.infrastructure;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.infrastructure.input.inbox.SnapshotParser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;

/** Shared base: wipes the test database before each test and builds domain snapshots from snapshot JSON. */
@SpringBootTest
@ActiveProfiles({"local", "test"})
public abstract class IntegrationTestSupport {

    @Autowired
    protected DatabaseClient db;

    @Autowired
    protected SnapshotParser parser;

    @BeforeEach
    void wipe() {
        db.sql("TRUNCATE project, roadmap_stage, openspec_change, task, objective_progress_point, sync_run, "
                + "repo_commit, task_closed RESTART IDENTITY CASCADE").then().block();
    }

    protected static String snapshotJson(String projects) {
        return """
                {"format_version": 2, "generator_version": "test", "generated_at": "2026-10-15T07:00:00-05:00",
                 "projects": %s}""".formatted(projects);
    }

    protected ProjectSnapshot snapshot(String id, String entryJson) {
        return parser.parse(snapshotJson("{\"" + id + "\": " + entryJson + "}")).valid().get(0);
    }

    protected Object one(String sql) {
        return db.sql(sql).map(row -> row.get(0)).one().block();
    }

    protected long count(String sql) {
        return ((Number) one(sql)).longValue();
    }
}
