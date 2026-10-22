package co.com.srdejo.agentproject.infrastructure.out.r2dbc;

import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.infrastructure.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V8 tables over the real Postgres: commit replacement per project and the closed-task history. */
class ActivityR2dbcAdapterIntegrationTest extends IntegrationTestSupport {

    private static final Instant T1 = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired
    private IActivityPersistencePort activity;

    @Test
    void replacingCommitsOnlyTouchesThatProject() {
        activity.replaceCommits("hotel", List.of(new RepoCommit("hotel", "a1", T1, "uno"))).block();
        activity.replaceCommits("nolost", List.of(new RepoCommit("nolost", "b1", T1, "otro"))).block();

        activity.replaceCommits("hotel", List.of(new RepoCommit("hotel", "a2", T2, "dos"),
                new RepoCommit("hotel", "a3", T1, "tres"))).block();

        assertThat(activity.findCommitsSince(T1).collectList().block())
                .extracting(RepoCommit::projectId, RepoCommit::sha)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("hotel", "a2"),
                        org.assertj.core.groups.Tuple.tuple("hotel", "a3"),
                        org.assertj.core.groups.Tuple.tuple("nolost", "b1"));
        assertThat(activity.findCommitsSince(T2).collectList().block()).extracting(RepoCommit::sha).containsExactly("a2");
    }

    @Test
    void closedTasksAreAppendedAndReadBackMostRecentFirst() {
        db.sql("INSERT INTO project (id, name, repo, state) VALUES ('nolost', 'NoLost', 'nolost', 'ACTIVE')").then().block();

        activity.saveClosed(List.of(new ClosedTask(null, "nolost", Task.TaskSource.OPENSPEC, "kids", "1.2", "Dos", T1)))
                .block();
        activity.saveClosed(List.of(new ClosedTask(null, "nolost", Task.TaskSource.ROADMAP, "Etapa 1", null, "B", T2)))
                .block();

        List<ClosedTask> closed = activity.findClosedSince(T1).collectList().block();
        assertThat(closed).extracting(ClosedTask::text).containsExactly("B", "Dos");
        assertThat(closed.get(1).source()).isEqualTo(Task.TaskSource.OPENSPEC);
        assertThat(closed.get(1).number()).isEqualTo("1.2");
        assertThat(closed.get(0).id()).isNotNull();
    }
}
