package ru.example.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.example.budget.annotation.Cumponent;
import java.time.Clock;

@Cumponent
@Configuration
public class TimeConfiguration {
    @Bean
    public Clock serverClock() {
        return Clock.systemUTC();
    }
}
