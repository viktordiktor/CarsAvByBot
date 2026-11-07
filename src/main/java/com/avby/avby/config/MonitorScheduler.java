package com.avby.avby.config;

import com.avby.avby.service.MonitorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "avby.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MonitorScheduler {

    private final MonitorService monitorService;

    public MonitorScheduler(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Scheduled(
            fixedDelayString = "${avby.scheduler-tick-ms:30000}",
            initialDelayString = "${avby.scheduler-initial-delay-ms:15000}"
    )
    public void tick() {
        monitorService.pollDueProfiles();
    }
}
