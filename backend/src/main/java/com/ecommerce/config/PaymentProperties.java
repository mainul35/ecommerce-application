package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the "payment" namespace with Spring Boot's configuration processor
 * so IDEs can validate application.yml entries and provide auto-completion.
 *
 * Individual gateway beans still read their values via @Value — this class
 * simply declares the namespace so the IDE doesn't flag it as unknown.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private Sslcommerz sslcommerz = new Sslcommerz();
    private Paypay paypay = new Paypay();
    private Omise omise = new Omise();
    private Linepay linepay = new Linepay();

    @Getter @Setter
    public static class Sslcommerz {
        private String storeId = "PLACEHOLDER";
        private String storePass = "PLACEHOLDER";
        private boolean sandbox = true;
    }

    @Getter @Setter
    public static class Paypay {
        private String apiKey = "PLACEHOLDER";
        private String apiSecret = "PLACEHOLDER";
        private boolean sandbox = true;
    }

    @Getter @Setter
    public static class Omise {
        private String publicKey = "PLACEHOLDER";
        private String secretKey = "PLACEHOLDER";
    }

    @Getter @Setter
    public static class Linepay {
        private String channelId = "PLACEHOLDER";
        private String channelSecret = "PLACEHOLDER";
        private boolean sandbox = true;
    }
}
