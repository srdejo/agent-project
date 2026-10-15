package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.view.SyncHealth;
import reactor.core.publisher.Mono;

public interface ISyncHealthServicePort {

    Mono<SyncHealth> health();
}
