package com.avby.avby.web;

import com.avby.avby.domain.AppSettings;

/**
 * Settings form. The bot token is never rendered back into the page: a blank
 * token means "keep the current one".
 */
public class SettingsForm {

    private String telegramBotToken;
    private String telegramChatId;
    private boolean telegramEnabled = true;
    private boolean sendPhoto = true;
    private int pollIntervalMinutes = 5;
    private int requestDelayMs = 1200;
    private boolean tokenConfigured;

    public static SettingsForm from(AppSettings settings) {
        SettingsForm form = new SettingsForm();
        form.setTelegramChatId(settings.getTelegramChatId());
        form.setTelegramEnabled(settings.isTelegramEnabled());
        form.setSendPhoto(settings.isSendPhoto());
        form.setPollIntervalMinutes(settings.getPollIntervalMinutes());
        form.setRequestDelayMs(settings.getRequestDelayMs());
        form.setTokenConfigured(settings.getTelegramBotToken() != null
                && !settings.getTelegramBotToken().isBlank());
        return form;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public boolean isTelegramEnabled() {
        return telegramEnabled;
    }

    public void setTelegramEnabled(boolean telegramEnabled) {
        this.telegramEnabled = telegramEnabled;
    }

    public boolean isSendPhoto() {
        return sendPhoto;
    }

    public void setSendPhoto(boolean sendPhoto) {
        this.sendPhoto = sendPhoto;
    }

    public int getPollIntervalMinutes() {
        return pollIntervalMinutes;
    }

    public void setPollIntervalMinutes(int pollIntervalMinutes) {
        this.pollIntervalMinutes = pollIntervalMinutes;
    }

    public int getRequestDelayMs() {
        return requestDelayMs;
    }

    public void setRequestDelayMs(int requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public boolean isTokenConfigured() {
        return tokenConfigured;
    }

    public void setTokenConfigured(boolean tokenConfigured) {
        this.tokenConfigured = tokenConfigured;
    }
}
