package co.com.srdejo.agentproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentProjectApplication.class, args);
    }
}
