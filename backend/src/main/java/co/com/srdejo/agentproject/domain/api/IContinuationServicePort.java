package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.view.Continuation;
import reactor.core.publisher.Mono;

public interface IContinuationServicePort {

    /** Empty when the project or the change does not exist. Never writes anything. */
    Mono<Continuation> getContinuation(String projectId, String changeName);
}
