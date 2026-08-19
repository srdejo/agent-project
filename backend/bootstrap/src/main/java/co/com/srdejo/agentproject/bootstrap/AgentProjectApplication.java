package co.com.srdejo.agentproject.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "co.com.srdejo.agentproject")
@EntityScan(basePackages = "co.com.srdejo.agentproject")
@EnableJpaRepositories(basePackages = "co.com.srdejo.agentproject")
@EnableScheduling
public class AgentProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentProjectApplication.class, args);
    }
}
