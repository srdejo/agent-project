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

    /**
     * Marker the sync uses to say "clear the stored {@code priorityRank}", mirroring the blank
     * string the textual portfolio fields use. A real rank is always 1 or greater, so {@code 0}
     * can never collide with a legitimate value.
     */
    public static final int PORTFOLIO_RANK_CLEARED = 0;

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

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "alias", length = 120)
    private String alias;

    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "priority_rank")
    private Integer priorityRank;

    @Column(name = "open_question", columnDefinition = "text")
    private String openQuestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> stack = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TaskItem> tasks = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TaskCheck> checks = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<AgentEvent> events = List.of();

    @Column(name = "source_last_modified")
    private Instant sourceLastModified;

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
                           String summary, List<String> stack, List<TaskItem> tasks,
                           List<TaskCheck> checks, List<AgentEvent> events, Portfolio portfolio,
                           Instant sourceLastModified, Instant now) {
        this.name = name;
        this.repo = repo;
        this.stage = stage;
        this.status = status;
        this.progress = progress;
        this.updatedLabel = updatedLabel;
        this.commitSha = commitSha;
        this.verifyStatus = verifyStatus;
        this.summary = summary;
        this.stack = stack;
        this.tasks = tasks;
        this.checks = checks;
        this.events = events;
        applyPortfolio(portfolio);
        this.sourceLastModified = sourceLastModified;
        this.updatedAt = now;
    }

    /**
     * Editorial layer of the portfolio: the sync never derives these, the user decides them.
     * A {@code null} {@code portfolio} — or a {@code null} field inside it — means "the JSON did
     * not carry that field", so the value already stored is kept. A blank incoming value means the
     * JSON carried it explicitly as {@code null} (or empty), so the stored value is cleared.
     */
    private void applyPortfolio(Portfolio portfolio) {
        if (portfolio == null) {
            return;
        }
        this.alias = mergePortfolioField(this.alias, portfolio.alias());
        this.priority = mergePortfolioField(this.priority, portfolio.priority());
        this.priorityRank = mergePortfolioRank(this.priorityRank, portfolio.priorityRank());
        this.openQuestion = mergePortfolioField(this.openQuestion, portfolio.openQuestion());
    }

    private static String mergePortfolioField(String current, String incoming) {
        if (incoming == null) {
            return current;
        }
        return incoming.isBlank() ? null : incoming;
    }

    /**
     * Integer counterpart of {@link #mergePortfolioField}. A blank string is the "clear me" marker
     * for the textual portfolio fields; for the rank the equivalent marker is
     * {@link #PORTFOLIO_RANK_CLEARED}, a value the domain can never hold since a valid rank is 1 or
     * greater. So {@code null} keeps what is stored, {@code 0} (or anything below 1) clears it.
     */
    private static Integer mergePortfolioRank(Integer current, Integer incoming) {
        if (incoming == null) {
            return current;
        }
        return incoming <= PORTFOLIO_RANK_CLEARED ? null : incoming;
    }

    public boolean hasSameLastModified(Instant sourceLastModified) {
        return sourceLastModified.equals(this.sourceLastModified);
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

    public String getSummary() {
        return summary;
    }

    public String getAlias() {
        return alias;
    }

    public String getPriority() {
        return priority;
    }

    public String getOpenQuestion() {
        return openQuestion;
    }

    public Integer getPriorityRank() {
        return priorityRank;
    }

    public List<String> getStack() {
        return stack;
    }

    public List<TaskItem> getTasks() {
        return tasks;
    }

    public List<TaskCheck> getChecks() {
        return checks;
    }

    public List<AgentEvent> getEvents() {
        return events;
    }

    public Instant getSourceLastModified() {
        return sourceLastModified;
    }

    /**
     * Optional, user-authored portfolio context. Each field is nullable and independent:
     * {@code null} means "absent from the sync JSON" (keep whatever is stored), while a blank
     * value means "explicitly cleared". {@code priorityRank} is an {@code Integer} instead of a
     * {@code String}, so its "explicitly cleared" marker is {@link #PORTFOLIO_RANK_CLEARED}
     * ({@code 0}) rather than the empty string — a valid rank is always 1 or greater.
     */
    public record Portfolio(String alias, String priority, Integer priorityRank, String openQuestion) {
    }

    public record TaskItem(String name, String stage, String status, String date, String commit) {
    }

    public record TaskCheck(String name, boolean ok, String duration) {
    }

    public record AgentEvent(String time, String mark, String text) {
    }
}
