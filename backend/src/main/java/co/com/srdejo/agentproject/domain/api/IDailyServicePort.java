package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.view.Daily;
import reactor.core.publisher.Mono;

public interface IDailyServicePort {

    /** Deterministic for the same data and day. Reads only. */
    Mono<Daily> getDaily();
}
