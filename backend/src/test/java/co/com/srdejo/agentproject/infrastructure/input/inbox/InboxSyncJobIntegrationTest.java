package co.com.srdejo.agentproject.infrastructure.input.inbox;

import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.infrastructure.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class InboxSyncJobIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ISyncServicePort syncService;

    @TempDir
    Path inbox;

    private InboxSyncJob job;

    @BeforeEach
    void newJob() {
        // Built by hand and never started: the test drives flush() itself, without the file watcher racing it.
        job = new InboxSyncJob(parser, syncService, Clock.systemUTC(), inbox.toString());
    }

    private void drop(String name, String content) throws IOException {
        Files.writeString(inbox.resolve(name), content);
    }

    private static String entry(String name) {
        return "{\"name\": \"" + name + "\", \"repo\": \"" + name + "\", \"state\": \"ACTIVE\"}";
    }

    @Test
    void validSnapshotCreatesProjectsRecordsTheRunAndDeletesTheFile() throws IOException {
        drop("snapshot.json", snapshotJson("{\"hotel\": " + entry("hotel") + ", \"bad\": {\"name\": \"x\"}, "
                + "\"contact\": {\"error\": \"sin permiso\"}}"));

        StepVerifier.create(job.flush()).assertNext(run -> {
            assertThat(run.created()).containsExactly("hotel");
            assertThat(run.rejected()).containsKey("bad");
            assertThat(run.readErrors()).containsKey("contact");
            assertThat(run.fileError()).isNull();
            assertThat(run.formatVersion()).isEqualTo(2);
            assertThat(run.generatorVersion()).isEqualTo("test");
        }).verifyComplete();

        assertThat(inbox.resolve("snapshot.json")).doesNotExist();
        assertThat(count("SELECT count(*) FROM project")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sync_run")).isEqualTo(1);
    }

    @Test
    void unreadableSnapshotLeavesAFileErrorRunAndTouchesNothing() throws IOException {
        drop("snapshot.json", snapshotJson("{\"hotel\": " + entry("hotel") + "}"));
        job.flush().blockLast();
        drop("snapshot.json", "{esto no es json");

        StepVerifier.create(job.flush())
                .assertNext(run -> assertThat(run.fileError()).startsWith("JSON inválido"))
                .verifyComplete();

        assertThat(count("SELECT count(*) FROM project")).isEqualTo(1);
        assertThat(inbox.resolve("snapshot.json")).doesNotExist();
    }

    @Test
    void obsoleteFormatIsRecordedAndDeleted() throws IOException {
        drop("progreso.json", "{\"hotel\": {}}");

        StepVerifier.create(job.flush())
                .assertNext(run -> assertThat(run.fileError()).isEqualTo("formato obsoleto: progreso.json"))
                .verifyComplete();

        assertThat(inbox.resolve("progreso.json")).doesNotExist();
        assertThat(count("SELECT count(*) FROM project")).isZero();
    }

    @Test
    void projectLeftOutOfAValidSnapshotIsMarkedMissing() throws IOException {
        drop("snapshot.json", snapshotJson("{\"hotel\": " + entry("hotel") + ", \"contact\": " + entry("contact") + "}"));
        job.flush().blockLast();
        drop("snapshot.json", snapshotJson("{\"hotel\": " + entry("hotel") + "}"));

        StepVerifier.create(job.flush())
                .assertNext(run -> assertThat(run.missing()).containsExactly("contact"))
                .verifyComplete();

        assertThat(one("SELECT missing_since IS NOT NULL FROM project WHERE id = 'contact'")).isEqualTo(true);
    }

    @Test
    void emptyInboxDoesNothing() {
        StepVerifier.create(job.flush()).verifyComplete();
        assertThat(count("SELECT count(*) FROM sync_run")).isZero();
    }
}
