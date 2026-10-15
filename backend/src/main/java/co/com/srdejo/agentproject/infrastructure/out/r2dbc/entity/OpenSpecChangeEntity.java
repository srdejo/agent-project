package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("openspec_change")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenSpecChangeEntity {

    @Id
    private Long id;
    private String projectId;
    private String name;
    private boolean archived;
    private int ordinal;
    private String metadata;
    private Json artifacts;
    private Json artifactIndex;
    private Instant lastParsedAt;
    private String parseError;
    private Instant parseErrorAt;
}
