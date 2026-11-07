package com.avby.avby.telegram;

import com.avby.avby.avby.AvbyListing;
import com.avby.avby.domain.AppSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Telegram Bot API client. No external Telegram library is needed.
 *
 * <p>Every result carries a {@link DeliveryStatus} so the caller can decide
 * whether a retry is safe. A retry is allowed only when Telegram definitely did
 * not accept the message (for example HTTP 429). Network timeouts and HTTP 5xx
 * responses are treated as ambiguous: the message may already have been
 * delivered, so it is never retried automatically.</p>
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final int CAPTION_LIMIT = 1024;
    private static final int MESSAGE_LIMIT = 4096;

    private final TelegramMessageFormatter formatter;
    private final String apiBase;
    private final HttpClient httpClient;
    private final Duration requestTimeout = Duration.ofSeconds(25);

    public TelegramClient(
            TelegramMessageFormatter formatter,
            @Value("${avby.telegram-api-base:https://api.telegram.org}") String apiBase
    ) {
        this.formatter = formatter;
        this.apiBase = trimTrailingSlash(apiBase);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public TelegramResult sendListing(AvbyListing listing, AppSettings settings) {
        if (settings == null || !settings.isTelegramEnabled()) {
            return TelegramResult.failed("Отправка в Telegram отключена", DeliveryStatus.PERMANENT);
        }
        String token = trimToNull(settings.getTelegramBotToken());
        List<String> chatIds = chatIds(settings.getTelegramChatId());
        if (token == null) {
            return TelegramResult.failed("Не задан токен Telegram-бота", DeliveryStatus.PERMANENT);
        }
        if (chatIds.isEmpty()) {
            return TelegramResult.failed("Не задан chat_id Telegram", DeliveryStatus.PERMANENT);
        }

        List<String> errors = new ArrayList<>();
        int sent = 0;
        boolean ambiguous = false;
        boolean retryable = false;
        for (String chatId : chatIds) {
            TelegramResult result = sendListingToChat(token, chatId, listing, settings.isSendPhoto());
            if (result.status() == DeliveryStatus.SENT) {
                sent++;
            } else {
                errors.add(chatId + ": " + result.message());
                if (result.status() == DeliveryStatus.AMBIGUOUS) {
                    ambiguous = true;
                } else if (result.status() == DeliveryStatus.RETRYABLE) {
                    retryable = true;
                }
            }
            sleep(250);
        }

        if (sent > 0) {
            // Partial success is treated as delivered. Retrying would duplicate
            // the message in the chats that already received it.
            if (sent == chatIds.size()) {
                return TelegramResult.sent("Отправлено в " + sent + " чат(ов)");
            }
            return TelegramResult.partial("Отправлено в " + sent + " из " + chatIds.size()
                    + " чатов; ошибки: " + String.join("; ", errors));
        }

        DeliveryStatus status = ambiguous
                ? DeliveryStatus.AMBIGUOUS
                : retryable ? DeliveryStatus.RETRYABLE : DeliveryStatus.PERMANENT;
        return TelegramResult.failed(String.join("; ", errors), status);
    }

    public TelegramResult sendTest(String token, String chatId) {
        String cleanToken = trimToNull(token);
        List<String> chatIds = chatIds(chatId);
        if (cleanToken == null) {
            return TelegramResult.failed("Не задан токен Telegram-бота", DeliveryStatus.PERMANENT);
        }
        if (chatIds.isEmpty()) {
            return TelegramResult.failed("Не задан chat_id Telegram", DeliveryStatus.PERMANENT);
        }
        String text = "✅ <b>av.by monitor</b>\nБот подключён. Новые объявления будут приходить сюда.";
        List<String> errors = new ArrayList<>();
        int sent = 0;
        for (String id : chatIds) {
            TelegramResult result = callSendMessage(cleanToken, id, text);
            if (result.status() == DeliveryStatus.SENT) {
                sent++;
            } else {
                errors.add(id + ": " + result.message());
            }
        }
        return sent == chatIds.size()
                ? TelegramResult.sent("Тестовое сообщение отправлено")
                : TelegramResult.failed(String.join("; ", errors), DeliveryStatus.PERMANENT);
    }

    public ChatsResult findChats(String token) {
        String cleanToken = trimToNull(token);
        if (cleanToken == null) {
            return new ChatsResult(false, "Сначала укажите токен бота", List.of());
        }
        try {
            String url = apiBase + "/bot" + cleanToken + "/getUpdates?timeout=0&limit=100";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                return new ChatsResult(false, errorDescription(json), List.of());
            }

            Map<Long, ChatInfo> chats = new LinkedHashMap<>();
            JsonArray updates = json.getAsJsonArray("result");
            if (updates != null) {
                for (JsonElement element : updates) {
                    JsonObject update = element.getAsJsonObject();
                    if (!update.has("message")) {
                        continue;
                    }
                    JsonObject message = update.getAsJsonObject("message");
                    if (!message.has("chat")) {
                        continue;
                    }
                    JsonObject chat = message.getAsJsonObject("chat");
                    long id = chat.get("id").getAsLong();
                    String title = firstNonBlank(
                            getString(chat, "title"),
                            getString(chat, "username"),
                            getString(chat, "first_name"),
                            "chat " + id
                    );
                    chats.put(id, new ChatInfo(id, title));
                }
            }
            return new ChatsResult(true, "Найдено чатов: " + chats.size(), new ArrayList<>(chats.values()));
        } catch (IOException e) {
            return new ChatsResult(false, "Ошибка сети: " + e.getMessage(), List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ChatsResult(false, "Запрос прерван", List.of());
        } catch (RuntimeException e) {
            return new ChatsResult(false, "Не удалось разобрать ответ Telegram: " + e.getMessage(), List.of());
        }
    }

    private TelegramResult sendListingToChat(String token, String chatId, AvbyListing listing, boolean sendPhoto) {
        if (sendPhoto && listing.imageUrl() != null && !listing.imageUrl().isBlank()) {
            String caption = formatter.format(listing, CAPTION_LIMIT);
            TelegramResult photoResult = callSendPhoto(token, chatId, listing.imageUrl(), caption, listing.url());
            if (photoResult.status() == DeliveryStatus.SENT) {
                return photoResult;
            }
            if (photoResult.status() != DeliveryStatus.PERMANENT) {
                // Ambiguous or retryable: sending the text as well could duplicate
                // the notification (or hit the same rate limit again).
                return photoResult;
            }
            log.debug("sendPhoto rejected, falling back to sendMessage: {}", photoResult.message());
        }
        String text = formatter.format(listing, MESSAGE_LIMIT);
        return callSendMessage(token, chatId, text, listing.url());
    }

    private TelegramResult callSendMessage(String token, String chatId, String text) {
        return callSendMessage(token, chatId, text, null);
    }

    private TelegramResult callSendMessage(String token, String chatId, String text, String buttonUrl) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("chat_id", chatId);
        form.put("text", text);
        form.put("parse_mode", "HTML");
        form.put("disable_web_page_preview", "true");
        if (buttonUrl != null && !buttonUrl.isBlank()) {
            form.put("reply_markup", inlineKeyboard(buttonUrl));
        }
        return callApi(token, "sendMessage", form);
    }

    private TelegramResult callSendPhoto(String token, String chatId, String photoUrl, String caption, String buttonUrl) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("chat_id", chatId);
        form.put("photo", photoUrl);
        form.put("caption", caption);
        form.put("parse_mode", "HTML");
        if (buttonUrl != null && !buttonUrl.isBlank()) {
            form.put("reply_markup", inlineKeyboard(buttonUrl));
        }
        return callApi(token, "sendPhoto", form);
    }

    private TelegramResult callApi(String token, String method, Map<String, String> form) {
        URI uri;
        try {
            uri = URI.create(apiBase + "/bot" + token + "/" + method);
        } catch (IllegalArgumentException e) {
            return TelegramResult.failed("Некорректный адрес Telegram API: " + e.getMessage(),
                    DeliveryStatus.PERMANENT);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            JsonObject json = parseJson(response.body());

            if (code >= 200 && code < 300) {
                if (json != null && json.has("ok") && json.get("ok").getAsBoolean()) {
                    return TelegramResult.sent("OK");
                }
                return TelegramResult.failed(
                        json != null ? errorDescription(json) : "Некорректный ответ Telegram (HTTP " + code + ")",
                        DeliveryStatus.PERMANENT);
            }

            String description = json != null
                    ? errorDescription(json)
                    : "Telegram вернул HTTP " + code;
            if (code == 429) {
                // Definitely not delivered: safe to retry later.
                return TelegramResult.failed(description, DeliveryStatus.RETRYABLE);
            }
            if (code >= 500) {
                // The request may have been processed before the server error.
                return TelegramResult.failed(description, DeliveryStatus.AMBIGUOUS);
            }
            if (code >= 400) {
                return TelegramResult.failed(description, DeliveryStatus.PERMANENT);
            }
            return TelegramResult.failed(description, DeliveryStatus.AMBIGUOUS);
        } catch (ConnectException e) {
            return TelegramResult.failed("Не удалось подключиться к Telegram: " + e.getMessage(),
                    DeliveryStatus.RETRYABLE);
        } catch (HttpConnectTimeoutException e) {
            return TelegramResult.failed("Не удалось подключиться к Telegram: " + e.getMessage(),
                    DeliveryStatus.RETRYABLE);
        } catch (IOException e) {
            // A request may have reached Telegram before the connection broke.
            return TelegramResult.failed("Ошибка сети Telegram: " + e.getMessage(), DeliveryStatus.AMBIGUOUS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TelegramResult.failed("Запрос в Telegram прерван", DeliveryStatus.AMBIGUOUS);
        } catch (RuntimeException e) {
            return TelegramResult.failed("Ошибка ответа Telegram: " + e.getMessage(), DeliveryStatus.AMBIGUOUS);
        }
    }

    private JsonObject parseJson(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String inlineKeyboard(String url) {
        JsonObject button = new JsonObject();
        button.addProperty("text", "Открыть на av.by");
        button.addProperty("url", url);
        JsonArray row = new JsonArray();
        row.add(button);
        JsonArray keyboard = new JsonArray();
        keyboard.add(row);
        JsonObject markup = new JsonObject();
        markup.add("inline_keyboard", keyboard);
        return markup.toString();
    }

    private String formEncode(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private String errorDescription(JsonObject json) {
        if (json.has("description")) {
            return json.get("description").getAsString();
        }
        return "Telegram вернул ошибку";
    }

    private List<String> chatIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    private String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.telegram.org";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Result of one Telegram operation.
     *
     * <ul>
     *   <li>{@code SENT} — confirmed delivery (all chats, or at least one chat
     *       when {@code partial} is true); never retry automatically.</li>
     *   <li>{@code RETRYABLE} — Telegram definitely did not accept it; safe to
     *       retry later.</li>
     *   <li>{@code PERMANENT} — Telegram rejected it and retrying is pointless.</li>
     *   <li>{@code AMBIGUOUS} — delivery is unknown; never retry automatically.</li>
     * </ul>
     */
    public enum DeliveryStatus {
        SENT,
        RETRYABLE,
        PERMANENT,
        AMBIGUOUS
    }

    public record TelegramResult(boolean ok, String message, DeliveryStatus status, boolean partial) {

        public static TelegramResult sent(String message) {
            return new TelegramResult(true, message, DeliveryStatus.SENT, false);
        }

        public static TelegramResult partial(String message) {
            return new TelegramResult(true, message, DeliveryStatus.SENT, true);
        }

        public static TelegramResult failed(String message, DeliveryStatus status) {
            return new TelegramResult(false, message, status, false);
        }
    }

    public record ChatInfo(long id, String title) {
    }

    public record ChatsResult(boolean ok, String message, List<ChatInfo> chats) {
    }
}
