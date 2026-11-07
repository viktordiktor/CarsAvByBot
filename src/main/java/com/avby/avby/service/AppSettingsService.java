package com.avby.avby.service;

import com.avby.avby.domain.AppSettings;
import com.avby.avby.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingsService {

    private final AppSettingsRepository repository;

    public AppSettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppSettings get() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(new AppSettings()));
    }

    /**
     * Updates only user-editable fields. A blank token means "keep the current
     * token", which lets the settings page render an empty password field.
     */
    @Transactional
    public AppSettings update(
            String telegramBotToken,
            String telegramChatId,
            boolean telegramEnabled,
            boolean sendPhoto,
            int pollIntervalMinutes,
            int requestDelayMs
    ) {
        AppSettings settings = get();
        if (telegramBotToken != null && !telegramBotToken.isBlank()) {
            settings.setTelegramBotToken(telegramBotToken.trim());
        }
        settings.setTelegramChatId(trimToNull(telegramChatId));
        settings.setTelegramEnabled(telegramEnabled);
        settings.setSendPhoto(sendPhoto);
        settings.setPollIntervalMinutes(Math.max(1, pollIntervalMinutes));
        settings.setRequestDelayMs(Math.max(300, requestDelayMs));
        return repository.save(settings);
    }

    @Transactional
    public void clearTelegramToken() {
        AppSettings settings = get();
        settings.setTelegramBotToken(null);
        repository.save(settings);
    }

    @Transactional
    public void recordPollStarted() {
        AppSettings settings = get();
        settings.setLastPollStartedAt(java.time.Instant.now());
        settings.setLastPollStatus("Выполняется…");
        repository.save(settings);
    }

    @Transactional
    public void recordPollFinished(String status) {
        AppSettings settings = get();
        settings.setLastPollFinishedAt(java.time.Instant.now());
        settings.setLastPollStatus(status);
        repository.save(settings);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
