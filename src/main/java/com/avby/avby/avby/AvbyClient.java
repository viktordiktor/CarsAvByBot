package com.avby.avby.avby;

import com.avby.avby.domain.FilterProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small HTTP client for cars.av.by. It intentionally looks like a normal
 * browser and uses a conservative retry policy.
 */
@Component
public class AvbyClient {

    private static final Logger log = LoggerFactory.getLogger(AvbyClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final Pattern TOTAL_COUNT = Pattern.compile(
            "На странице\\s+\\d+\\s+объявлений из\\s+([\\d\\s\\u00A0]+)");

    private final AvbyFilterUrlBuilder urlBuilder;
    private final AvbyParser parser;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final int maxAttempts = 3;

    public AvbyClient(
            AvbyFilterUrlBuilder urlBuilder,
            AvbyParser parser,
            @Value("${avby.request-timeout-seconds:30}") long timeoutSeconds,
            @Value("${avby.proxy-url:}") String proxyUrl
    ) {
        this.urlBuilder = urlBuilder;
        this.parser = parser;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        configureProxy(builder, proxyUrl);
        this.httpClient = builder.build();
    }

    /**
     * Optional outbound HTTP proxy, e.g. {@code http://user:pass@host:8080}.
     * av.by sits behind a SafeLine WAF that challenges datacenter IPs with
     * HTTP 468, so a proxy (or a host with a clean IP) may be required.
     */
    private static void configureProxy(HttpClient.Builder builder, String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return;
        }
        URI uri = URI.create(proxyUrl.trim());
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("avby.proxy-url must look like http://host:port");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 8080;
        builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] credentials = userInfo.split(":", 2);
            String username = credentials[0];
            String password = credentials.length > 1 ? credentials[1] : "";
            // Java disables Basic auth for HTTPS CONNECT tunnels by default.
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                    return null;
                }
            });
        }
        log.info("av.by requests will use proxy {}:{}", host, port);
    }

    public PageResult fetchPage(FilterProfile profile, int page) {
        String url = urlBuilder.build(profile, page);
        String html = fetch(url);
        List<AvbyListing> listings = parser.parse(html);
        int total = parseTotalCount(html);
        return new PageResult(listings, total, url);
    }

    public String fetch(String url) {
        IOException lastIoException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(requestTimeout)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                        .header("Cache-Control", "no-cache")
                        .header("Referer", "https://cars.av.by/filter")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                if (status == 403 || status == 429) {
                    throw new AvbyException("av.by ограничил доступ (HTTP " + status
                            + "). Увеличьте интервал опроса или попробуйте позже.");
                }
                if (status == 468) {
                    log.warn("av.by returned HTTP 468 (SafeLine anti-bot challenge) for {}", url);
                    throw new AvbyException("av.by требует проверку браузера (HTTP 468, SafeLine WAF). "
                            + "Скорее всего, IP хостинга заблокирован. Запустите бота на домашнем IP/VPS "
                            + "или задайте прокси в AVBY_PROXY_URL.");
                }
                if (status >= 500) {
                    log.warn("av.by temporary error HTTP {}, attempt {}/{}", status, attempt, maxAttempts);
                } else {
                    throw new AvbyException("av.by вернул HTTP " + status + " для " + url);
                }
            } catch (IOException e) {
                lastIoException = e;
                log.warn("av.by request failed ({}), attempt {}/{}", e.getMessage(), attempt, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AvbyException("Запрос к av.by был прерван", e);
            }

            if (attempt < maxAttempts) {
                sleep(1500L * attempt);
            }
        }
        throw new AvbyException("Не удалось загрузить av.by после " + maxAttempts + " попыток", lastIoException);
    }

    public int parseTotalCount(String html) {
        Matcher matcher = TOTAL_COUNT.matcher(html);
        if (!matcher.find()) {
            return 0;
        }
        String digits = matcher.group(1).replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record PageResult(List<AvbyListing> listings, int totalCount, String url) {
    }
}
