package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.view.Work;
import reactor.core.publisher.Mono;

public interface IWorkServicePort {

    Mono<Work> getWork();
}
