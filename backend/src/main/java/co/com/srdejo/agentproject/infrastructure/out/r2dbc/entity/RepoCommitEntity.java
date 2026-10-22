package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Composite key (project_id, sha): written and read with explicit queries, never through save(). */
@Table("repo_commit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepoCommitEntity {

    private String projectId;
    private String sha;
    private Instant committedAt;
    private String subject;
}
