package co.com.srdejo.agentproject.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "sync_runs")
public class SyncRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> createdIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "updated_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> updatedIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unchanged_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> unchangedIds = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rejected = Map.of();

    protected SyncRunEntity() {
        // required by Hibernate
    }

    public static SyncRunEntity of(List<String> createdIds, List<String> updatedIds, List<String> unchangedIds,
                                    Map<String, String> rejected, Instant ranAt) {
        SyncRunEntity entity = new SyncRunEntity();
        entity.createdIds = createdIds;
        entity.updatedIds = updatedIds;
        entity.unchangedIds = unchangedIds;
        entity.rejected = rejected;
        entity.ranAt = ranAt;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public List<String> getCreatedIds() {
        return createdIds;
    }

    public List<String> getUpdatedIds() {
        return updatedIds;
    }

    public List<String> getUnchangedIds() {
        return unchangedIds;
    }

    public Map<String, String> getRejected() {
        return rejected;
    }
}
