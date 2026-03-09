package neobank.infrastructure.adapter.ses;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.config.SesConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SesAdapter {

    private final SesClient sesClient;
    private final SesConfig sesConfig;

    public void sendEmail(String to, String subject, String body) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(sesConfig.getFromEmail())
                    .destination(Destination.builder()
                            .toAddresses(to)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(body)
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);

            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Error sending email", e);
            throw new RuntimeException("Error sending email", e);
        }
    }
}