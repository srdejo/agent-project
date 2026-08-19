package co.com.srdejo.agentproject.projects.repository;

import co.com.srdejo.agentproject.projects.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, String> {
}
