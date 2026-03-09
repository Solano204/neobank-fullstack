package neobank.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import neobank.application.service.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final SessionService sessionService;

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanExpiredSessions() {
        log.info("Starting scheduled task: Clean expired sessions");
        sessionService.cleanExpiredSessions();
        log.info("Completed scheduled task: Clean expired sessions");
    }
}