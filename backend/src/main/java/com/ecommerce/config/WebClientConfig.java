package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient forexWebClient(@Value("${forex.api-url:https://api.frankfurter.app/latest}") String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    WebClient ollamaWebClient(OllamaProperties props) {
        return WebClient.builder()
                .baseUrl(props.getUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
