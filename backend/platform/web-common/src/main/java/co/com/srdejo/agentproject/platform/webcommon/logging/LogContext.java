package co.com.srdejo.agentproject.platform.webcommon.logging;

import org.slf4j.MDC;

/**
 * Contrato del contexto de log de una peticion, para que quien aporta un dato no dependa de MDC
 * ni de las claves como texto suelto.
 *
 * El ciclo de vida lo controla RequestLoggingFilter: el es el unico que limpia. Quien aporta un
 * dato no limpia, porque el filtro externo siempre ejecuta su finally y el hilo vuelve limpio al
 * pool aunque la cadena falle a mitad de camino.
 */
public final class LogContext {

    public static final String REQUEST_ID = "requestId";

    private LogContext() {
    }

    public static void putRequestId(String requestId) {
        MDC.put(REQUEST_ID, requestId);
    }

    public static void clear() {
        MDC.clear();
    }
}
