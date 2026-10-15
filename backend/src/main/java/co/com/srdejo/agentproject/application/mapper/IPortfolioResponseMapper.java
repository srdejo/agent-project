package co.com.srdejo.agentproject.application.mapper;

import co.com.srdejo.agentproject.application.dto.response.ChangeDetailResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ChangeListResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ContinuationResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectCardResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectDetailResponseDto;
import co.com.srdejo.agentproject.domain.model.view.ChangeDetail;
import co.com.srdejo.agentproject.domain.model.view.ChangeList;
import co.com.srdejo.agentproject.domain.model.view.Continuation;
import co.com.srdejo.agentproject.domain.model.view.ProjectCard;
import co.com.srdejo.agentproject.domain.model.view.ProjectDetail;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface IPortfolioResponseMapper {

    ProjectCardResponseDto toResponse(ProjectCard card);

    ProjectDetailResponseDto toResponse(ProjectDetail detail);

    ChangeListResponseDto toResponse(ChangeList changes);

    ChangeDetailResponseDto toResponse(ChangeDetail change);

    ContinuationResponseDto toResponse(Continuation continuation);
}
