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
    /** Base URL of the running Ollama server. */
    private String url = "http://localhost:11434";
    /** Embedding model to use, e.g. nomic-embed-text or mxbai-embed-large. */
    private String model = "nomic-embed-text";
    /** Output dimensions of the selected model — must match the vector(N) column in the DB. */
    private int dimensions = 768;
}
