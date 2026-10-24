package com.ronrob.brti.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MarketPollingConfig {

    @Bean
    public Clock marketClock() {
        return Clock.systemUTC();
    }
}
