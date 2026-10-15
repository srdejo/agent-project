package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.ProjectEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface IProjectRepository extends ReactiveCrudRepository<ProjectEntity, String> {
}
