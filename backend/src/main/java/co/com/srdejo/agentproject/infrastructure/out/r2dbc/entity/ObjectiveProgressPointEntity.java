package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("objective_progress_point")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ObjectiveProgressPointEntity {

    @Id
    private Long id;
    private String projectId;
    private String objectiveName;
    private int done;
    private int total;
    private Instant takenAt;
}
