package co.com.srdejo.agentproject.platform.webcommon.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RequestLoggingConfig {

    /**
     * HIGHEST_PRECEDENCE para quedar por fuera de cualquier filtro de seguridad que se agregue
     * despues (Boot los registra en -100): por dentro no veria las peticiones que ese filtro
     * llegara a rechazar, que son justamente las que hay que poder diagnosticar.
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
