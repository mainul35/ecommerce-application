package com.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "verification")
public class VerificationProperties {

    /** From-address on verification emails. */
    private String fromEmail = "no-reply@ecommerce.local";

    /** Email link token validity, in hours. */
    private long emailTtlHours = 24;

    /** Phone OTP validity, in minutes. */
    private long phoneOtpTtlMinutes = 10;

    /**
     * Phone verification is a DUMMY for now (real bulk SMS needs a paid
     * subscription). When true, the generated OTP is logged instead of sent,
     * and {@link #dummyPhoneOtp} (if set) is always accepted - so the flow is
     * fully testable without an SMS provider.
     */
    private boolean phoneDummy = true;

    /** A fixed OTP that always passes while {@link #phoneDummy} is true. */
    private String dummyPhoneOtp = "000000";
}
