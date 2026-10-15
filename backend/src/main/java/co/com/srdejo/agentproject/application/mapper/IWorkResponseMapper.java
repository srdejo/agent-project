package co.com.srdejo.agentproject.application.mapper;

import co.com.srdejo.agentproject.application.dto.response.SyncHealthResponseDto;
import co.com.srdejo.agentproject.application.dto.response.WorkResponseDto;
import co.com.srdejo.agentproject.domain.model.view.SyncHealth;
import co.com.srdejo.agentproject.domain.model.view.Work;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface IWorkResponseMapper {

    WorkResponseDto toResponse(Work work);

    SyncHealthResponseDto toResponse(SyncHealth health);
}
