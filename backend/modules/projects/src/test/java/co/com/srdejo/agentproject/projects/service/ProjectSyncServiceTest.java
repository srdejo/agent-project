package co.com.srdejo.agentproject.projects.service;

import co.com.srdejo.agentproject.projects.api.ProjectSyncRequest;
import co.com.srdejo.agentproject.projects.api.SyncOutcome;
import co.com.srdejo.agentproject.projects.model.ProjectEntity;
import co.com.srdejo.agentproject.projects.model.ProjectSnapshotEntity;
import co.com.srdejo.agentproject.projects.repository.ProjectJpaRepository;
import co.com.srdejo.agentproject.projects.repository.ProjectSnapshotJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSyncServiceTest {

    @Mock
    private ProjectJpaRepository projects;

    @Mock
    private ProjectSnapshotJpaRepository snapshots;

    private ProjectSyncService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProjectSyncService(projects, snapshots);
    }

    @Test
    void existsDelegatesToRepository() {
        when(projects.existsById("agent-project")).thenReturn(true);

        assertThat(service.exists("agent-project")).isTrue();
    }

    @Test
    void createsANewProjectWhenItDoesNotExistYet() {
        when(projects.findById("agent-project")).thenReturn(Optional.empty());
        ProjectSyncRequest request = request(Instant.parse("2026-08-19T10:00:00Z"));

        SyncOutcome outcome = service.applySync(request);

        assertThat(outcome).isEqualTo(SyncOutcome.CREATED);

        ArgumentCaptor<ProjectEntity> entityCaptor = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projects).save(entityCaptor.capture());
        ProjectEntity saved = entityCaptor.getValue();
        assertThat(saved.getId()).isEqualTo("agent-project");
        assertThat(saved.getName()).isEqualTo("Agent Project");
        assertThat(saved.getProgress()).isEqualTo(42);

        verify(snapshots).save(any(ProjectSnapshotEntity.class));
    }

    @Test
    void updatesAnExistingProjectWhenLastModifiedChanged() {
        ProjectEntity existing = ProjectEntity.create("agent-project", Instant.parse("2026-08-18T00:00:00Z"));
        existing.applySync("Old name", "repo", "stage", "STARTED", 10, "updated", "sha", "PENDING",
                "summary", List.of(), List.of(), List.of(), List.of(),
                Instant.parse("2026-08-18T10:00:00Z"), Instant.parse("2026-08-18T00:00:00Z"));
        when(projects.findById("agent-project")).thenReturn(Optional.of(existing));

        ProjectSyncRequest request = request(Instant.parse("2026-08-19T10:00:00Z"));

        SyncOutcome outcome = service.applySync(request);

        assertThat(outcome).isEqualTo(SyncOutcome.UPDATED);
        assertThat(existing.getName()).isEqualTo("Agent Project");
        assertThat(existing.getProgress()).isEqualTo(42);
        verify(projects).save(existing);
        verify(snapshots).save(any(ProjectSnapshotEntity.class));
    }

    @Test
    void returnsUnchangedWhenLastModifiedIsTheSame() {
        Instant sameInstant = Instant.parse("2026-08-19T10:00:00Z");
        ProjectEntity existing = ProjectEntity.create("agent-project", Instant.parse("2026-08-18T00:00:00Z"));
        existing.applySync("Agent Project", "repo", "stage", "IN_PROGRESS", 42, "updated", "sha", "PASSED",
                "summary", List.of(), List.of(), List.of(), List.of(),
                sameInstant, Instant.parse("2026-08-18T00:00:00Z"));
        when(projects.findById("agent-project")).thenReturn(Optional.of(existing));

        ProjectSyncRequest request = request(sameInstant);

        SyncOutcome outcome = service.applySync(request);

        assertThat(outcome).isEqualTo(SyncOutcome.UNCHANGED);
        verify(projects, never()).save(any());
        verify(snapshots, never()).save(any());
    }

    private ProjectSyncRequest request(Instant lastModified) {
        return new ProjectSyncRequest(
                "agent-project",
                "Agent Project",
                "agent-project",
                "backend",
                "IN_PROGRESS",
                42,
                "today",
                "abc123",
                "PASSED",
                "on track",
                List.of("java", "angular"),
                List.of(new ProjectSyncRequest.Task("sync job", "backend", "done", "2026-08-19", "abc123")),
                List.of(new ProjectSyncRequest.Check("unit tests", true, "12s")),
                List.of(new ProjectSyncRequest.Event("10:00", "note", "started")),
                lastModified
        );
    }
}
