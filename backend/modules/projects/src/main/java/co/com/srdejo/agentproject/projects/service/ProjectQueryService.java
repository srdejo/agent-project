package co.com.srdejo.agentproject.projects.service;

import co.com.srdejo.agentproject.projects.model.ProjectEntity;
import co.com.srdejo.agentproject.projects.model.ProjectSnapshotEntity;
import co.com.srdejo.agentproject.projects.repository.ProjectJpaRepository;
import co.com.srdejo.agentproject.projects.repository.ProjectSnapshotJpaRepository;
import co.com.srdejo.agentproject.projects.web.ProjectDetailResponse;
import co.com.srdejo.agentproject.projects.web.ProjectListResponse;
import co.com.srdejo.agentproject.projects.web.ProjectNotFoundException;
import co.com.srdejo.agentproject.projects.web.ProjectSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectQueryService {

    private final ProjectJpaRepository projects;
    private final ProjectSnapshotJpaRepository snapshots;

    public ProjectQueryService(ProjectJpaRepository projects, ProjectSnapshotJpaRepository snapshots) {
        this.projects = projects;
        this.snapshots = snapshots;
    }

    public ProjectListResponse listAll() {
        List<ProjectEntity> all = projects.findAll();

        List<ProjectSummaryResponse> summaries = all.stream()
                .map(this::toSummary)
                .toList();

        int count = all.size();
        int avg = count == 0 ? 0 : (int) Math.round(all.stream().mapToInt(ProjectEntity::getProgress).average().orElse(0));
        int blocked = all.stream().mapToInt(p -> p.getBlocked().size()).sum();
        int verified = all.stream().mapToInt(p -> p.getCompleted().size()).sum();

        return new ProjectListResponse(summaries, new ProjectListResponse.Stats(count, avg, blocked, verified));
    }

    public ProjectDetailResponse getById(String id) {
        ProjectEntity entity = projects.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
        List<ProjectSnapshotEntity> history = snapshots.findByProjectIdOrderByTakenAtAsc(id);

        return new ProjectDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getRepo(),
                entity.getProgress(),
                entity.getStage(),
                entity.getStatus(),
                entity.getUpdatedLabel(),
                entity.getCommitSha(),
                entity.getVerifyStatus(),
                entity.getCompleted(),
                entity.getNextTasks(),
                entity.getBlocked(),
                entity.getChecks().stream()
                        .map(c -> new ProjectDetailResponse.Check(c.name(), c.ok(), c.duration()))
                        .toList(),
                entity.getEvents().stream()
                        .map(e -> new ProjectDetailResponse.Event(e.time(), e.mark(), e.text()))
                        .toList(),
                history.stream()
                        .map(s -> new ProjectDetailResponse.Snapshot(s.getTakenAt().toString(), s.getProgress()))
                        .toList()
        );
    }

    private ProjectSummaryResponse toSummary(ProjectEntity entity) {
        List<Integer> series = snapshots.findTop14ByProjectIdOrderByTakenAtDesc(entity.getId()).stream()
                .sorted(Comparator.comparing(ProjectSnapshotEntity::getTakenAt))
                .map(ProjectSnapshotEntity::getProgress)
                .toList();

        return new ProjectSummaryResponse(
                entity.getId(),
                entity.getName(),
                entity.getRepo(),
                entity.getProgress(),
                entity.getStage(),
                entity.getStatus(),
                entity.getUpdatedLabel(),
                series,
                entity.getEvents().stream()
                        .map(e -> new ProjectSummaryResponse.Event(e.time(), e.mark(), e.text()))
                        .toList()
        );
    }
}
