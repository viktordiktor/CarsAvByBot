package com.avby.avby.telegram;

import com.avby.avby.avby.AvbyListing;
import com.avby.avby.domain.AppSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramClientTest {

    private HttpServer server;
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedDeque<StubResponse> responses = new ConcurrentLinkedDeque<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPaths.add(exchange.getRequestURI().getPath());
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();

            StubResponse response = responses.pollFirst();
            if (response == null) {
                response = new StubResponse(200, "{\"ok\":true,\"result\":{}}");
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void partialMultiChatSuccessCountsAsDelivered() {
        responses.add(new StubResponse(200, "{\"ok\":true}"));
        responses.add(new StubResponse(400, "{\"ok\":false,\"description\":\"Bad Request\"}"));

        TelegramClient.TelegramResult result = client()
                .sendListing(listing(), settings("1,2", false));

        assertThat(result.ok()).isTrue();
        assertThat(result.status()).isEqualTo(TelegramClient.DeliveryStatus.SENT);
        assertThat(result.partial()).isTrue();
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void http500IsAmbiguousAndMustNotBeRetried() {
        responses.add(new StubResponse(500, "{\"ok\":false,\"description\":\"Internal Server Error\"}"));

        TelegramClient.TelegramResult result = client()
                .sendListing(listing(), settings("1", false));

        assertThat(result.ok()).isFalse();
        assertThat(result.status()).isEqualTo(TelegramClient.DeliveryStatus.AMBIGUOUS);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void http429IsRetryable() {
        responses.add(new StubResponse(429, "{\"ok\":false,\"description\":\"Too Many Requests\"}"));

        TelegramClient.TelegramResult result = client()
                .sendListing(listing(), settings("1", false));

        assertThat(result.ok()).isFalse();
        assertThat(result.status()).isEqualTo(TelegramClient.DeliveryStatus.RETRYABLE);
    }

    @Test
    void photoIsRetriedAsTextOnlyOnPermanentRejection() {
        responses.add(new StubResponse(400, "{\"ok\":false,\"description\":\"Bad photo\"}"));
        responses.add(new StubResponse(200, "{\"ok\":true}"));

        TelegramClient.TelegramResult result = client()
                .sendListing(listing(), settings("1", true));

        assertThat(result.status()).isEqualTo(TelegramClient.DeliveryStatus.SENT);
        assertThat(requestPaths).hasSize(2);
        assertThat(requestPaths.get(0)).endsWith("/sendPhoto");
        assertThat(requestPaths.get(1)).endsWith("/sendMessage");
    }

    @Test
    void ambiguousPhotoFailureDoesNotFallBackToText() {
        responses.add(new StubResponse(500, "{\"ok\":false,\"description\":\"Internal Server Error\"}"));

        TelegramClient.TelegramResult result = client()
                .sendListing(listing(), settings("1", true));

        assertThat(result.status()).isEqualTo(TelegramClient.DeliveryStatus.AMBIGUOUS);
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(requestPaths.get(0)).endsWith("/sendPhoto");
    }

    private TelegramClient client() {
        return new TelegramClient(
                new TelegramMessageFormatter(),
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
    }

    private AppSettings settings(String chatIds, boolean sendPhoto) {
        AppSettings settings = new AppSettings();
        settings.setTelegramBotToken("123:TEST");
        settings.setTelegramChatId(chatIds);
        settings.setTelegramEnabled(true);
        settings.setSendPhoto(sendPhoto);
        return settings;
    }

    private AvbyListing listing() {
        return new AvbyListing(
                "1001",
                "BMW X5",
                "https://cars.av.by/bmw/x5/1001",
                "10 000 $",
                "2015 г.",
                "автомат",
                "200 000 км",
                "Минск",
                "сегодня",
                "Срочно",
                "https://example.com/photo.jpg",
                null
        );
    }

    private record StubResponse(int status, String body) {
    }
}
