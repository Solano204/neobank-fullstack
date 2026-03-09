package neobank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "neobank.domain.repository")
@EntityScan(basePackages = "neobank.domain.entity")
public class NeobankApplication {
    public static void main(String[] args) {
        SpringApplication.run(NeobankApplication.class, args);
    }
}