package co.com.srdejo.agentproject.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "project_snapshots")
public class ProjectSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private int progress;

    private String stage;

    @Column(nullable = false)
    private String status;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    protected ProjectSnapshotEntity() {
        // required by Hibernate
    }

    public static ProjectSnapshotEntity of(String projectId, int progress, String stage, String status, Instant takenAt) {
        ProjectSnapshotEntity entity = new ProjectSnapshotEntity();
        entity.projectId = projectId;
        entity.progress = progress;
        entity.stage = stage;
        entity.status = status;
        entity.takenAt = takenAt;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public int getProgress() {
        return progress;
    }

    public String getStage() {
        return stage;
    }

    public String getStatus() {
        return status;
    }

    public Instant getTakenAt() {
        return takenAt;
    }
}
