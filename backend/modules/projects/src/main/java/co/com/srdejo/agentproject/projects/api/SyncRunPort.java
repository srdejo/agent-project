package co.com.srdejo.agentproject.projects.api;

/**
 * Public entry point for recording an inbox sync run as a whole. Implemented by {@code modules:projects},
 * called once per poll cycle by {@code modules:progress}'s {@code InboxSyncJob} after both inbox files
 * (progreso.json / nuevo.json) have been processed.
 */
public interface SyncRunPort {

    void record(SyncRunRecord run);
}
