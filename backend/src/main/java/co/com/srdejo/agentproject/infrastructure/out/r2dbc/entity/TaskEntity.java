package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    private Long id;
    private String projectId;
    private String source;
    private Long stageId;
    private Long changeId;
    private int ordinal;
    private String number;
    private String text;
    private String section;
    private int line;
    private boolean checked;
    private boolean cancelled;
    private String status;
    private String blockedReason;
    private boolean inObjective;
    private String heldByBlocker;
}
