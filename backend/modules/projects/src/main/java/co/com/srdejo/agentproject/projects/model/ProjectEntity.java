package co.com.srdejo.agentproject.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String repo;

    private String stage;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "updated_label")
    private String updatedLabel;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "verify_status")
    private String verifyStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> completed = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "next_tasks", nullable = false, columnDefinition = "jsonb")
    private List<String> nextTasks = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> blocked = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TaskCheck> checks = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<AgentEvent> events = List.of();

    @Column(name = "last_sync_hash")
    private String lastSyncHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEntity() {
        // required by Hibernate
    }

    public static ProjectEntity create(String id, Instant now) {
        ProjectEntity entity = new ProjectEntity();
        entity.id = id;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void applySync(String name, String repo, String stage, String status, int progress,
                           String updatedLabel, String commitSha, String verifyStatus,
                           List<String> completed, List<String> nextTasks, List<String> blocked,
                           List<TaskCheck> checks, List<AgentEvent> events, String syncHash, Instant now) {
        this.name = name;
        this.repo = repo;
        this.stage = stage;
        this.status = status;
        this.progress = progress;
        this.updatedLabel = updatedLabel;
        this.commitSha = commitSha;
        this.verifyStatus = verifyStatus;
        this.completed = completed;
        this.nextTasks = nextTasks;
        this.blocked = blocked;
        this.checks = checks;
        this.events = events;
        this.lastSyncHash = syncHash;
        this.updatedAt = now;
    }

    public boolean hasSameSyncHash(String syncHash) {
        return syncHash.equals(this.lastSyncHash);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRepo() {
        return repo;
    }

    public String getStage() {
        return stage;
    }

    public String getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public String getUpdatedLabel() {
        return updatedLabel;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getVerifyStatus() {
        return verifyStatus;
    }

    public List<String> getCompleted() {
        return completed;
    }

    public List<String> getNextTasks() {
        return nextTasks;
    }

    public List<String> getBlocked() {
        return blocked;
    }

    public List<TaskCheck> getChecks() {
        return checks;
    }

    public List<AgentEvent> getEvents() {
        return events;
    }

    public record TaskCheck(String name, boolean ok, String duration) {
    }

    public record AgentEvent(String time, String mark, String text) {
    }
}
