package com.ecommerce.service;

import com.ecommerce.config.AppProperties;
import com.ecommerce.config.VerificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sends account emails. JavaMailSender is blocking, so sends run on
 * boundedElastic. Sends are BEST-EFFORT: a delivery failure (e.g. the dev
 * SMTP server is down) is logged but never fails the calling flow - the
 * token is already persisted and the user can resend.
 *
 * Dev target is Mailpit (SMTP localhost:1025, web inbox :8025). Point
 * spring.mail.* at a real SMTP provider for production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;
    private final VerificationProperties verificationProperties;

    public Mono<Void> sendEmailVerification(String toEmail, String token) {
        String link = appProperties.getFrontendUrl() + "/verify-email?token=" + token;
        String body = """
                Welcome to our marketplace!

                Please confirm your email address by opening the link below:

                %s

                If you did not create an account, you can ignore this message.
                """.formatted(link);

        return send(toEmail, "Verify your email address", body)
                .doOnSuccess(v -> log.info("Verification email dispatched to {}", toEmail));
    }

    private Mono<Void> send(String to, String subject, String body) {
        return Mono.fromRunnable(() -> {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(verificationProperties.getFromEmail());
                    message.setTo(to);
                    message.setSubject(subject);
                    message.setText(body);
                    mailSender.send(message);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("Email send failed (to {}): {} - user can resend", to, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
