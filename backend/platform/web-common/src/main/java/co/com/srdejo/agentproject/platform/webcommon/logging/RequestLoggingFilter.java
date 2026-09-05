package co.com.srdejo.agentproject.platform.webcommon.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Se registra por fuera de cualquier filtro de seguridad futuro (ver RequestLoggingConfig), asi
 * que la duracion medida es la real de extremo a extremo y quedarian registradas tambien las
 * peticiones que un filtro de auth llegara a rechazar antes de cualquier controlador.
 *
 * El requestId se genera aca y no se acepta del cliente: un encabezado externo entraria sin
 * validar a cada linea de log.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        LogContext.putRequestId(requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response.getStatus(), System.currentTimeMillis() - startedAt);
            LogContext.clear();
        }
    }

    /**
     * El monitoreo consulta el actuator cada pocos segundos; registrarlo solo llenaria el journal
     * de ruido que no dice nada del negocio.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private void logRequest(HttpServletRequest request, int status, long elapsedMs) {
        String query = request.getQueryString();
        String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        if (status >= 500) {
            log.error("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
        } else if (status >= 400) {
            log.warn("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
        } else {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, elapsedMs);
        }
    }
}
