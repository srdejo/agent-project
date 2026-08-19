package co.com.srdejo.agentproject.projects.api;

/**
 * Public entry point for applying an inbox sync onto a project. Implemented by {@code modules:projects},
 * called by {@code modules:progress}'s {@code InboxSyncJob}.
 */
public interface ProjectSyncPort {

    SyncOutcome applySync(ProjectSyncRequest request);
}
