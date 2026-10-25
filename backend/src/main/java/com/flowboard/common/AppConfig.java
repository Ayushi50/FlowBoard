package com.flowboard.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injectable clock so date-sensitive logic (overdue, due today) is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
