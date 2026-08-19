package co.com.srdejo.agentproject.projects.repository;

import co.com.srdejo.agentproject.projects.model.SyncRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncRunJpaRepository extends JpaRepository<SyncRunEntity, Long> {

    Optional<SyncRunEntity> findTopByOrderByRanAtDesc();
}
