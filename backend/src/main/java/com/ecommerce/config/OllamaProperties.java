package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    /** Base URL of the Ollama server. Local: http://localhost:11434. Cloud: the Ollama Cloud endpoint. */
    private String url = "http://localhost:11434";
    /** Embedding model to use, e.g. nomic-embed-text or mxbai-embed-large. */
    private String model = "nomic-embed-text";
    /** Output dimensions of the selected model — must match the vector(N) column in the DB. */
    private int dimensions = 768;
    /**
     * API key for hosted inference (Ollama Cloud). Sent as {@code Authorization: Bearer}.
     * Leave blank for a local Ollama server, which needs no auth.
     */
    private String apiKey = "";
}
