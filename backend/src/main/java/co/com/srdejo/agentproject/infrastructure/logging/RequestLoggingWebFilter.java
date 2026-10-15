package co.com.srdejo.agentproject.infrastructure.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One log line per request, with status and duration, so {@code journalctl -u agent-project} shows traffic.
 * The request id is generated here and never taken from the client: an external header would reach every log
 * line unvalidated. On WebFlux the MDC cannot follow the request across threads, so it is set only around the
 * line this filter writes.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingWebFilter implements WebFilter {

    public static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern TOKEN_PARAM = Pattern.compile("((?:^|&)token=)[^&]*");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        return chain.filter(exchange)
                .doFinally(signal -> logRequest(exchange, requestId, System.currentTimeMillis() - startedAt));
    }

    /** The agent authenticates with {@code ?token=…}: it must never reach the journal. */
    static String redact(String query) {
        return TOKEN_PARAM.matcher(query).replaceAll("$1***");
    }

    private void logRequest(ServerWebExchange exchange, String requestId, long elapsedMs) {
        ServerHttpRequest request = exchange.getRequest();
        var statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode == null ? 200 : statusCode.value();
        String query = request.getURI().getRawQuery();
        String path = query == null ? request.getPath().value() : request.getPath().value() + "?" + redact(query);
        MDC.put(REQUEST_ID, requestId);
        try {
            if (status >= 500) {
                log.error("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
            } else if (status >= 400) {
                log.warn("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
            } else {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
            }
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }
}
