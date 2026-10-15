package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("sync_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncRunEntity {

    @Id
    private Long id;
    private Instant startedAt;
    private long durationMs;
    private Integer formatVersion;
    private String generatorVersion;
    private Instant generatedAt;
    private String fileError;
    private Json createdIds;
    private Json updatedIds;
    private Json unchangedIds;
    private Json missingIds;
    private Json rejected;
    private Json readErrors;
    private Json warnings;
}
