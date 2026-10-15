package co.com.srdejo.agentproject.infrastructure.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingWebFilterTest {

    @Test
    void agentTokenNeverReachesTheLog() {
        assertThat(RequestLoggingWebFilter.redact("role=agent&token=s3cr3t")).isEqualTo("role=agent&token=***");
        assertThat(RequestLoggingWebFilter.redact("token=s3cr3t&role=agent")).isEqualTo("token=***&role=agent");
        assertThat(RequestLoggingWebFilter.redact("includeArchived=true")).isEqualTo("includeArchived=true");
        assertThat(RequestLoggingWebFilter.redact("mytoken=x")).isEqualTo("mytoken=x");
    }
}
