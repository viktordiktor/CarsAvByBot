package com.avby.avby.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Global application settings. Only one row (id = 1) is used.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(length = 200)
    private String telegramBotToken;

    /** One chat id or several comma-separated chat ids. */
    @Column(length = 500)
    private String telegramChatId;

    private boolean telegramEnabled = true;

    private boolean sendPhoto = true;

    private int pollIntervalMinutes = 5;

    private int requestDelayMs = 1200;

    private Instant lastPollStartedAt;

    private Instant lastPollFinishedAt;

    @Column(length = 1000)
    private String lastPollStatus;
}
