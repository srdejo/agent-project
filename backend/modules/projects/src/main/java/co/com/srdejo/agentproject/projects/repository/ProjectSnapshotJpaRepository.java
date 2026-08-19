package co.com.srdejo.agentproject.projects.repository;

import co.com.srdejo.agentproject.projects.model.ProjectSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSnapshotJpaRepository extends JpaRepository<ProjectSnapshotEntity, Long> {

    List<ProjectSnapshotEntity> findTop14ByProjectIdOrderByTakenAtDesc(String projectId);

    List<ProjectSnapshotEntity> findByProjectIdOrderByTakenAtAsc(String projectId);
}
