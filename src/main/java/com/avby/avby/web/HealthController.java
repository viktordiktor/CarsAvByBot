package com.avby.avby.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny liveness endpoint. Free hosting plans (Render, Railway, etc.) spin a
 * service down when it receives no inbound HTTP traffic, regardless of the
 * scheduler running inside the process. Point an external uptime pinger at
 * {@code /health} to keep the monitor awake.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
