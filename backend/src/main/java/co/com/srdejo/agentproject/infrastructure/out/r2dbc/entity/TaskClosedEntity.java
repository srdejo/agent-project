package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("task_closed")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskClosedEntity {

    @Id
    private Long id;
    private String projectId;
    private String source;
    private String scope;
    private String number;
    private String text;
    private Instant closedAt;
}
