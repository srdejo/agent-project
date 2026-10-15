package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.view.ChangeDetail;
import co.com.srdejo.agentproject.domain.model.view.ChangeList;
import co.com.srdejo.agentproject.domain.model.view.ProjectCard;
import co.com.srdejo.agentproject.domain.model.view.ProjectDetail;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Read side of the portfolio. Unknown project or change ids complete empty. */
public interface IPortfolioServicePort {

    Flux<ProjectCard> getPortfolio(boolean includeArchived);

    Mono<ProjectDetail> getProject(String projectId);

    Mono<ChangeList> getChanges(String projectId);

    Mono<ChangeDetail> getChange(String projectId, String changeName);
}
