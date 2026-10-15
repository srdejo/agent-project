package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;

/** The id comes from the generator, so {@link Persistable} tells R2DBC whether to insert or update. */
@Table("project")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity implements Persistable<String> {

    @Id
    private String id;
    private String name;
    private String repo;
    private String alias;
    private String summary;
    private String state;
    private Integer priorityRank;
    private Json stack;
    private String contentHash;
    private int progressRuleVersion;
    private String sourceCommit;
    private Instant sourceModifiedAt;
    private Instant missingSince;
    private String readError;
    private Instant readErrorAt;
    private boolean roadmapDetected;
    private int roadmapDone;
    private int roadmapTotal;
    private String objectiveName;
    private String objectiveCriteria;
    private LocalDate objectiveTargetDate;
    private Json objectiveScope;
    private int objectiveDone;
    private int objectiveTotal;
    private Json achievedObjectives;
    private Json blockers;
    private boolean openspecDetected;
    private int openspecSpecsCount;
    private int openspecArchivedCount;
    private Json openspecCommands;
    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
