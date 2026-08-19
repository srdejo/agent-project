package co.com.srdejo.agentproject.projects.service;

import co.com.srdejo.agentproject.projects.api.SyncRunPort;
import co.com.srdejo.agentproject.projects.api.SyncRunRecord;
import co.com.srdejo.agentproject.projects.model.SyncRunEntity;
import co.com.srdejo.agentproject.projects.repository.SyncRunJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncRunRecordingService implements SyncRunPort {

    private final SyncRunJpaRepository syncRuns;

    public SyncRunRecordingService(SyncRunJpaRepository syncRuns) {
        this.syncRuns = syncRuns;
    }

    @Override
    @Transactional
    public void record(SyncRunRecord run) {
        syncRuns.save(SyncRunEntity.of(run.createdIds(), run.updatedIds(), run.unchangedIds(), run.rejected(), run.ranAt()));
    }
}
