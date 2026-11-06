package ru.example.budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.example.budget.annotation.Cumponent;
import ru.example.budget.config.LocalEnvLoader;

@Cumponent
@SpringBootApplication
public class BudgetApplication {
    public static void main(String[] args) {
        try {
            LocalEnvLoader.load();
            ru.example.budget.db.DatabaseBootstrap.runIfRequested();
        } catch (Exception exception) {
            throw new IllegalStateException("Database bootstrap failed", exception);
        }
        SpringApplication.run(BudgetApplication.class, args);
    }
}
