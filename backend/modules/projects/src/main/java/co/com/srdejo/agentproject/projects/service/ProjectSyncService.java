package co.com.srdejo.agentproject.projects.service;

import co.com.srdejo.agentproject.projects.api.ProjectSyncPort;
import co.com.srdejo.agentproject.projects.api.ProjectSyncRequest;
import co.com.srdejo.agentproject.projects.api.SyncOutcome;
import co.com.srdejo.agentproject.projects.model.ProjectEntity;
import co.com.srdejo.agentproject.projects.model.ProjectSnapshotEntity;
import co.com.srdejo.agentproject.projects.repository.ProjectJpaRepository;
import co.com.srdejo.agentproject.projects.repository.ProjectSnapshotJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectSyncService implements ProjectSyncPort {

    private final ProjectJpaRepository projects;
    private final ProjectSnapshotJpaRepository snapshots;

    public ProjectSyncService(ProjectJpaRepository projects, ProjectSnapshotJpaRepository snapshots) {
        this.projects = projects;
        this.snapshots = snapshots;
    }

    @Override
    public boolean exists(String id) {
        return projects.existsById(id);
    }

    @Override
    @Transactional
    public SyncOutcome applySync(ProjectSyncRequest request) {
        Instant now = Instant.now();
        boolean isNew = projects.findById(request.id()).isEmpty();
        ProjectEntity entity = projects.findById(request.id())
                .orElseGet(() -> ProjectEntity.create(request.id(), now));

        if (!isNew && entity.hasSameLastModified(request.lastModified())) {
            return SyncOutcome.UNCHANGED;
        }

        entity.applySync(
                request.name(),
                request.repo(),
                request.stage(),
                request.status(),
                request.progress(),
                request.updatedLabel(),
                request.commitSha(),
                request.verifyStatus(),
                request.completed(),
                request.nextTasks(),
                request.blocked(),
                toChecks(request.checks()),
                toEvents(request.events()),
                request.lastModified(),
                now
        );
        projects.save(entity);
        snapshots.save(ProjectSnapshotEntity.of(request.id(), request.progress(), request.stage(), request.status(), now));

        return isNew ? SyncOutcome.CREATED : SyncOutcome.UPDATED;
    }

    private List<ProjectEntity.TaskCheck> toChecks(List<ProjectSyncRequest.Check> checks) {
        return checks.stream()
                .map(c -> new ProjectEntity.TaskCheck(c.name(), c.ok(), c.duration()))
                .toList();
    }

    private List<ProjectEntity.AgentEvent> toEvents(List<ProjectSyncRequest.Event> events) {
        return events.stream()
                .map(e -> new ProjectEntity.AgentEvent(e.time(), e.mark(), e.text()))
                .toList();
    }
}
