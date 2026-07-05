package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
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
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(props.getUrl())
                .defaultHeader("Content-Type", "application/json");
        // Hosted inference (Ollama Cloud) requires a bearer token; a local Ollama
        // server needs none, so only attach the header when a key is configured.
        if (StringUtils.hasText(props.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey());
        }
        return builder.build();
    }
}
