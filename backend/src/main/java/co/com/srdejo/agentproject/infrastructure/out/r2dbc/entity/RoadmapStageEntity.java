package co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("roadmap_stage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapStageEntity {

    @Id
    private Long id;
    private String projectId;
    private int ordinal;
    @Column("stage_key")
    private String key;
    private String title;
    private String marker;
    private boolean hasCriteria;
    private boolean backlog;
}
