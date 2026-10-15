package co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.ObjectiveProgressPointEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.OpenSpecChangeEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.ProjectEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.RoadmapStageEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.SyncRunEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.TaskEntity;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * Entity ⇄ domain mapping. Written by hand rather than with MapStruct because most of it is JSONB
 * (de)serialization of generic types, which MapStruct would only delegate back here anyway.
 */
public class R2dbcEntityMapper {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<Project.ScopeRef>> SCOPE = new TypeReference<>() { };
    private static final TypeReference<List<Project.AchievedObjective>> ACHIEVED = new TypeReference<>() { };
    private static final TypeReference<List<Project.Blocker>> BLOCKERS = new TypeReference<>() { };
    private static final TypeReference<Change.Artifacts> ARTIFACTS = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<Map<String, List<String>>> WARNINGS = new TypeReference<>() { };

    private final JsonbCodec json;

    public R2dbcEntityMapper(JsonbCodec json) {
        this.json = json;
    }

    public ProjectEntity toEntity(Project p, boolean isNew) {
        Project.Objective o = p.objective();
        return ProjectEntity.builder()
                .id(p.id()).name(p.name()).repo(p.repo()).alias(p.alias()).summary(p.summary()).state(p.state())
                .priorityRank(p.priorityRank()).stack(json.write(p.stack())).contentHash(p.contentHash())
                .progressRuleVersion(p.ruleVersion()).sourceCommit(p.sourceCommit())
                .sourceModifiedAt(p.sourceModifiedAt()).missingSince(p.missingSince()).readError(p.readError())
                .readErrorAt(p.readErrorAt()).roadmapDetected(p.roadmapDetected()).roadmapDone(p.roadmapDone())
                .roadmapTotal(p.roadmapTotal())
                .objectiveName(o == null ? null : o.name())
                .objectiveCriteria(o == null ? null : o.criteria())
                .objectiveTargetDate(o == null ? null : o.targetDate())
                .objectiveScope(json.write(o == null ? List.of() : o.scope()))
                .objectiveDone(o == null ? 0 : o.done())
                .objectiveTotal(o == null ? 0 : o.total())
                .achievedObjectives(json.write(p.achievedObjectives())).blockers(json.write(p.blockers()))
                .openspecDetected(p.openspecDetected()).openspecSpecsCount(p.openspecSpecsCount())
                .openspecArchivedCount(p.openspecArchivedCount()).openspecCommands(json.write(p.openspecCommands()))
                .createdAt(p.createdAt()).updatedAt(p.updatedAt())
                .newEntity(isNew)
                .build();
    }

    public Project toProject(ProjectEntity e) {
        Project.Objective objective = e.getObjectiveName() == null ? null : new Project.Objective(e.getObjectiveName(),
                e.getObjectiveCriteria(), e.getObjectiveTargetDate(), json.read(e.getObjectiveScope(), SCOPE, List.of()),
                e.getObjectiveDone(), e.getObjectiveTotal());
        return new Project(e.getId(), e.getName(), e.getRepo(), e.getAlias(), e.getSummary(), e.getState(),
                e.getPriorityRank(), json.read(e.getStack(), STRINGS, List.of()), e.getContentHash(),
                e.getProgressRuleVersion(), e.getSourceCommit(), e.getSourceModifiedAt(), e.getMissingSince(),
                e.getReadError(), e.getReadErrorAt(), e.isRoadmapDetected(), e.getRoadmapDone(), e.getRoadmapTotal(),
                objective, json.read(e.getAchievedObjectives(), ACHIEVED, List.of()),
                json.read(e.getBlockers(), BLOCKERS, List.of()), e.isOpenspecDetected(), e.getOpenspecSpecsCount(),
                e.getOpenspecArchivedCount(), json.read(e.getOpenspecCommands(), STRINGS, List.of()), e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public RoadmapStageEntity toEntity(String projectId, Stage s) {
        return new RoadmapStageEntity(null, projectId, s.ordinal(), s.key(), s.title(), s.marker(), s.hasCriteria(),
                s.backlog());
    }

    public Stage toStage(RoadmapStageEntity e) {
        return new Stage(e.getOrdinal(), e.getKey(), e.getTitle(), e.getMarker(), e.isHasCriteria(), e.isBacklog());
    }

    public OpenSpecChangeEntity toEntity(String projectId, Change c) {
        return new OpenSpecChangeEntity(null, projectId, c.name(), c.archived(), c.ordinal(), c.metadata(),
                json.write(c.artifacts()), json.write(c.artifactIndex()), c.lastParsedAt(), c.parseError(),
                c.parseErrorAt());
    }

    public Change toChange(OpenSpecChangeEntity e) {
        return new Change(e.getName(), e.isArchived(), e.getOrdinal(), e.getMetadata(),
                json.read(e.getArtifacts(), ARTIFACTS, null), json.read(e.getArtifactIndex(), STRINGS, List.of()),
                e.getLastParsedAt(), e.getParseError(), e.getParseErrorAt());
    }

    public TaskEntity toEntity(Task t, Long stageId, Long changeId) {
        return new TaskEntity(null, t.projectId(), t.source().name(), stageId, changeId, t.ordinal(), t.number(),
                t.text(), t.section(), t.line(), t.checked(), t.cancelled(), t.status().name(), t.blockedReason(),
                t.inObjective(), t.heldByBlocker());
    }

    public Task toTask(TaskEntity e, Integer stageOrdinal, String changeName) {
        return new Task(e.getId(), e.getProjectId(), Task.TaskSource.valueOf(e.getSource()), stageOrdinal, changeName,
                e.getOrdinal(), e.getNumber(), e.getText(), e.getSection(), e.getLine(), e.isChecked(),
                e.isCancelled(), TaskStatus.valueOf(e.getStatus()), e.getBlockedReason(), e.isInObjective(),
                e.getHeldByBlocker());
    }

    public ObjectiveProgressPointEntity toEntity(ProgressPoint p) {
        return new ObjectiveProgressPointEntity(null, p.projectId(), p.objectiveName(), p.done(), p.total(), p.takenAt());
    }

    public ProgressPoint toPoint(ObjectiveProgressPointEntity e) {
        return new ProgressPoint(e.getProjectId(), e.getObjectiveName(), e.getDone(), e.getTotal(), e.getTakenAt());
    }

    public SyncRunEntity toEntity(SyncRun r) {
        return new SyncRunEntity(null, r.startedAt(), r.durationMs(), r.formatVersion(), r.generatorVersion(),
                r.generatedAt(), r.fileError(), json.write(r.created()), json.write(r.updated()),
                json.write(r.unchanged()), json.write(r.missing()), json.write(r.rejected()),
                json.write(r.readErrors()), json.write(r.warnings()));
    }

    public SyncRun toRun(SyncRunEntity e) {
        return new SyncRun(e.getStartedAt(), e.getDurationMs(), e.getFormatVersion(), e.getGeneratorVersion(),
                e.getGeneratedAt(), e.getFileError(), json.read(e.getCreatedIds(), STRINGS, List.of()),
                json.read(e.getUpdatedIds(), STRINGS, List.of()), json.read(e.getUnchangedIds(), STRINGS, List.of()),
                json.read(e.getMissingIds(), STRINGS, List.of()), json.read(e.getRejected(), STRING_MAP, Map.of()),
                json.read(e.getReadErrors(), STRING_MAP, Map.of()), json.read(e.getWarnings(), WARNINGS, Map.of()));
    }
}
