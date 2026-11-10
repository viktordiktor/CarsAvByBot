package com.avby.avby.avby;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Optional Playwright-based loader for av.by. The site is protected by a
 * SafeLine WAF which may answer a plain HTTP client with 468 and a JavaScript
 * challenge. A real browser executes that challenge and, if the challenge does
 * not require human interaction, continues to the requested page.
 *
 * <p>Disabled by default; enable with {@code AVBY_BROWSER_ENABLED=true}.</p>
 */
@Component
public class AvbyBrowserClient {

    private static final Logger log = LoggerFactory.getLogger(AvbyBrowserClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final boolean enabled;
    private final Duration timeout;
    private final String proxyUrl;
    private final Object lock = new Object();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

    public AvbyBrowserClient(
            @Value("${avby.browser-enabled:false}") boolean enabled,
            @Value("${avby.browser-timeout-seconds:60}") long timeoutSeconds,
            @Value("${avby.proxy-url:}") String proxyUrl
    ) {
        this.enabled = enabled;
        this.timeout = Duration.ofSeconds(Math.max(15, timeoutSeconds));
        this.proxyUrl = proxyUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String fetch(String url) {
        if (!enabled) {
            throw new AvbyException("Браузерный режим av.by выключен");
        }
        synchronized (lock) {
            ensureStarted();
            long deadline = System.nanoTime() + timeout.toNanos();
            Page page = context.newPage();
            try {
                page.setDefaultTimeout(timeout.toMillis());
                Response response = page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.COMMIT)
                        .setTimeout((double) timeout.toMillis()));

                int status = response != null ? response.status() : -1;
                log.info("av.by browser loaded {} (HTTP {})", url, status);

                long reloadAt = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                boolean reloaded = false;
                while (System.nanoTime() < deadline) {
                    String html = safeContent(page);
                    if (isRealPage(html)) {
                        return html;
                    }
                    if (!reloaded && System.nanoTime() > reloadAt) {
                        // SafeLine normally sets its cookie and reloads by itself;
                        // reload once as a fallback in case that did not happen.
                        log.info("SafeLine challenge is still active, reloading the page");
                        reload(page);
                        reloaded = true;
                    }
                    sleep(1000);
                }
                throw new AvbyException("av.by не прошёл браузерную проверку за "
                        + timeout.toSeconds() + " с. Похоже, SafeLine требует интерактивную "
                        + "проверку (капчу/слайдер). Запустите бота с чистого IP или через прокси.");
            } catch (PlaywrightException e) {
                throw new AvbyException("Не удалось загрузить av.by через браузер: "
                        + e.getMessage(), e);
            } finally {
                closeQuietly(page);
            }
        }
    }

    private void ensureStarted() {
        if (browser != null && browser.isConnected()) {
            return;
        }
        closeResources();
        log.info("Starting Playwright Chromium for av.by");
        playwright = Playwright.create();
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-extensions",
                        "--disable-background-networking",
                        "--disable-sync",
                        "--mute-audio",
                        "--no-first-run",
                        // Keep Chromium small: one renderer and a capped V8 heap.
                        "--renderer-process-limit=1",
                        "--js-flags=--max-old-space-size=64",
                        "--disable-features=site-per-process",
                        "--disable-software-rasterizer"));
        Proxy proxy = buildProxy();
        if (proxy != null) {
            launchOptions.setProxy(proxy);
            log.info("Playwright will load av.by through the configured proxy");
        }
        browser = playwright.chromium().launch(launchOptions);
        context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setLocale("ru-RU")
                .setTimezoneId("Europe/Minsk")
                .setViewportSize(1366, 900)
                .setExtraHTTPHeaders(Map.of("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")));
        context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
        // Images, fonts and media are not needed for parsing and cost a lot of memory.
        context.route("**/*", route -> {
            String type = route.request().resourceType();
            if ("image".equals(type) || "media".equals(type) || "font".equals(type)) {
                route.abort();
            } else {
                route.resume();
            }
        });
    }

    /**
     * Reuses {@code avby.proxy-url} for the browser, so a single setting covers
     * both the plain HTTP client and the Playwright fallback.
     */
    private Proxy buildProxy() {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(proxyUrl.trim());
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("avby.proxy-url must look like http://host:port");
        }
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri.getPort() > 0 ? uri.getPort() : 8080;
        Proxy proxy = new Proxy(scheme + "://" + host + ":" + port);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] credentials = userInfo.split(":", 2);
            proxy.setUsername(credentials[0]);
            if (credentials.length > 1) {
                proxy.setPassword(credentials[1]);
            }
        }
        return proxy;
    }

    private void reload(Page page) {
        try {
            page.reload(new Page.ReloadOptions()
                    .setWaitUntil(WaitUntilState.COMMIT)
                    .setTimeout((double) timeout.toMillis()));
        } catch (PlaywrightException e) {
            log.debug("Reload failed: {}", e.getMessage());
        }
    }

    private String safeContent(Page page) {        try {
            return page.content();
        } catch (PlaywrightException e) {
            // The challenge page may be reloading; try again on the next tick.
            return "";
        }
    }

    private boolean isRealPage(String html) {
        return html.contains("__NEXT_DATA__") || html.contains("listing-item");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AvbyException("Ожидание браузерной проверки av.by прервано", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            closeResources();
        }
    }

    private void closeResources() {
        closeQuietly(context);
        closeQuietly(browser);
        closeQuietly(playwright);
        context = null;
        browser = null;
        playwright = null;
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            log.debug("Error closing Playwright resource: {}", e.getMessage());
        }
    }
}
