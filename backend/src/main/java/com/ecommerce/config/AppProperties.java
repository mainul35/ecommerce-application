package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    /** Directory where uploaded product media files are stored. */
    private String uploadDir = "uploads";

    /** Frontend base URL — used by MockGateway to build the mock-pay redirect URL. */
    private String frontendUrl = "http://localhost:5173";
}
