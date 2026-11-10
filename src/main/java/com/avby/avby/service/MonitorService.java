package com.avby.avby.service;

import com.avby.avby.avby.AvbyClient;
import com.avby.avby.avby.AvbyListing;
import com.avby.avby.domain.AppSettings;
import com.avby.avby.domain.FilterProfile;
import com.avby.avby.domain.SeenListing;
import com.avby.avby.repository.SeenListingRepository;
import com.avby.avby.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private static final int PAGE_SIZE = 25;
    private static final int MAX_NOTIFY_ATTEMPTS = 3;
    /** Do not try to catch up more than this after a long outage. */
    private static final long MAX_CATCHUP_MINUTES = 60;
    /** Small overlap covers listings published while the previous poll was running. */
    private static final long WINDOW_OVERLAP_SECONDS = 30;

    private final AvbyClient avbyClient;
    private final TelegramClient telegramClient;
    private final FilterProfileService profileService;
    private final AppSettingsService settingsService;
    private final SeenListingRepository seenListingRepository;

    /**
     * Last successful poll per profile. This is intentionally in memory: after
     * a restart the monitor starts from "now minus one interval" instead of
     * replaying everything that is missing from the (possibly wiped) database.
     */
    private final Map<Long, Instant> lastSuccessfulPoll = new ConcurrentHashMap<>();

    /** One poll at a time keeps av.by requests and Telegram messages predictable. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MonitorService(
            AvbyClient avbyClient,
            TelegramClient telegramClient,
            FilterProfileService profileService,
            AppSettingsService settingsService,
            SeenListingRepository seenListingRepository
    ) {
        this.avbyClient = avbyClient;
        this.telegramClient = telegramClient;
        this.profileService = profileService;
        this.settingsService = settingsService;
        this.seenListingRepository = seenListingRepository;
    }

    public void pollDueProfiles() {
        AppSettings settings;
        List<FilterProfile> profiles;
        try {
            settings = settingsService.get();
            profiles = profileService.findEnabled();
        } catch (Exception e) {
            log.error("Cannot load profiles/settings for polling", e);
            return;
        }

        int defaultInterval = Math.max(1, settings.getPollIntervalMinutes());
        Instant now = Instant.now();
        int checked = 0;
        for (FilterProfile profile : profiles) {
            int interval = profile.getPollIntervalMinutes() != null
                    ? Math.max(1, profile.getPollIntervalMinutes())
                    : defaultInterval;
            Instant last = profile.getLastCheckedAt();
            if (last != null && last.plusSeconds(interval * 60L).isAfter(now)) {
                continue;
            }
            checked++;
            try {
                pollProfile(profile, false);
            } catch (Exception e) {
                log.error("Polling profile {} failed", profile.getId(), e);
            }
        }
        if (checked > 0) {
            log.info("Scheduled poll finished, profiles checked: {}", checked);
        }
    }

    public PollSummary pollProfile(Long profileId, boolean manual) {
        FilterProfile profile = profileService.get(profileId);
        return pollProfile(profile, manual);
    }

    /**
     * Manual "check everything now" action. Intervals are ignored.
     */
    public void pollAllNow() {
        for (FilterProfile profile : profileService.findEnabled()) {
            try {
                pollProfile(profile, true);
            } catch (Exception e) {
                log.error("Manual poll of profile {} failed", profile.getId(), e);
            }
        }
    }

    /**
     * Re-sends a previously seen listing to Telegram (used from the history
     * page). This is the only path that intentionally sends a duplicate, and it
     * is always confirmed by the user in the UI.
     */
    public PollSummary resend(Long seenListingId) {
        SeenListing seen = seenListingRepository.findById(seenListingId)
                .orElseThrow(() -> new IllegalArgumentException("Объявление не найдено"));
        AppSettings settings = settingsService.get();
        if (!isTelegramReady(settings)) {
            return new PollSummary(false, "Telegram не настроен: укажите токен и chat_id",
                    0, 0, 0, 0, 0, 0);
        }
        AvbyListing listing = fromEntity(seen);
        DeliveryOutcome outcome = deliver(seen, listing, settings);

        return switch (outcome.status()) {
            case SENT -> new PollSummary(true,
                    outcome.partial()
                            ? "Объявление отправлено, но не во все чаты: " + outcome.message()
                            : "Объявление отправлено в Telegram",
                    1, 0, 1, 0, 0, 0);
            case AMBIGUOUS -> new PollSummary(false,
                    "Отправка не подтверждена, автоматически повторять не будем: " + outcome.message(),
                    1, 0, 0, 1, 0, 1);
            case RETRYABLE, PERMANENT -> new PollSummary(false,
                    "Не удалось отправить: " + outcome.message(),
                    1, 0, 0, 1, 0, 0);
        };
    }

    public PollSummary pollProfile(FilterProfile profile, boolean manual) {
        if (!running.compareAndSet(false, true)) {
            return new PollSummary(false, "Проверка уже выполняется. Подождите завершения.",
                    0, 0, 0, 0, 0, 0);
        }

        long started = System.currentTimeMillis();
        try {
            settingsService.recordPollStarted();
            AppSettings settings = settingsService.get();

            int intervalMinutes = profile.getPollIntervalMinutes() != null
                    ? Math.max(1, profile.getPollIntervalMinutes())
                    : Math.max(1, settings.getPollIntervalMinutes());
            Instant now = Instant.now();
            Instant windowStart = windowStartFor(profile, intervalMinutes, now);

            int pages = Math.max(1, Math.min(10, profile.getPagesToScan()));
            int delayMs = Math.max(300, settings.getRequestDelayMs());
            List<AvbyListing> fetched = new ArrayList<>();
            int totalCount = 0;

            for (int page = 1; page <= pages; page++) {
                AvbyClient.PageResult pageResult = avbyClient.fetchPage(profile, page);
                if (page == 1) {
                    totalCount = pageResult.totalCount();
                }
                fetched.addAll(pageResult.listings());
                if (pageResult.listings().isEmpty()) {
                    break;
                }
                // Results are sorted by "newest activity" (bumps included), so
                // once the whole page is older than the window, later pages are
                // older too.
                if (isPageOlderThanWindow(pageResult.listings(), windowStart)) {
                    break;
                }
                if (totalCount > 0 && page * PAGE_SIZE >= totalCount) {
                    break;
                }
                if (page < pages) {
                    sleep(delayMs);
                }
            }

            Map<String, AvbyListing> unique = new LinkedHashMap<>();
            for (AvbyListing listing : fetched) {
                unique.putIfAbsent(listing.advertId(), listing);
            }
            List<AvbyListing> listings = new ArrayList<>(unique.values());
            List<AvbyListing> fresh = listings.stream()
                    .filter(listing -> isFresh(listing, windowStart))
                    .toList();

            long withoutDate = listings.stream()
                    .filter(listing -> listing.publishedAt() == null)
                    .count();
            if (withoutDate > 0) {
                log.warn("Profile {}: {} listings without publishedAt were skipped "
                        + "(av.by markup may have changed)", profile.getId(), withoutDate);
            }

            boolean telegramReady = isTelegramReady(settings);

            int newCount = 0;
            int sent = 0;
            int failed = 0;
            int skipped = 0;
            int unconfirmed = 0;
            Set<Long> attemptedThisPoll = new HashSet<>();

            for (AvbyListing listing : fresh) {
                if (seenListingRepository.existsByProfileIdAndAdvertId(
                        profile.getId(), listing.advertId())) {
                    continue;
                }
                SeenListing seen = toEntity(profile.getId(), listing);
                newCount++;

                if (seenListingRepository.existsByAdvertIdAndTelegramClaimedTrue(listing.advertId())) {
                    // Another profile already owns delivery for this advert.
                    markAlreadyClaimed(seen, listing.advertId());
                    skipped++;
                    seenListingRepository.save(seen);
                    continue;
                }
                if (!telegramReady) {
                    seen.setNotified(true);
                    seen.setNotifyError("Telegram не настроен");
                    skipped++;
                    seenListingRepository.save(seen);
                    continue;
                }

                DeliveryOutcome outcome = deliver(seen, listing, settings);
                attemptedThisPoll.add(seen.getId());
                switch (outcome.status()) {
                    case SENT -> sent++;
                    case AMBIGUOUS -> {
                        failed++;
                        unconfirmed++;
                    }
                    case RETRYABLE, PERMANENT -> failed++;
                }
            }

            if (telegramReady) {
                List<SeenListing> pending = seenListingRepository
                        .findTop50ByProfileIdAndNotifiedFalseAndTelegramClaimedFalseAndNotifyAttemptsLessThanOrderByFirstSeenAtAsc(
                                profile.getId(), MAX_NOTIFY_ATTEMPTS);
                for (SeenListing seen : pending) {
                    // Rows that failed earlier in this same poll are retried on
                    // the next poll, not immediately.
                    if (seen.getId() != null && attemptedThisPoll.contains(seen.getId())) {
                        continue;
                    }
                    if (seenListingRepository.existsByAdvertIdAndTelegramClaimedTrueAndIdNot(
                            seen.getAdvertId(), seen.getId())) {
                        markAlreadyClaimed(seen, seen.getAdvertId());
                        skipped++;
                        seenListingRepository.save(seen);
                        continue;
                    }
                    AvbyListing listing = unique.get(seen.getAdvertId());
                    if (listing == null) {
                        listing = fromEntity(seen);
                    }
                    DeliveryOutcome outcome = deliver(seen, listing, settings);
                    switch (outcome.status()) {
                        case SENT -> sent++;
                        case AMBIGUOUS -> {
                            failed++;
                            unconfirmed++;
                        }
                        case RETRYABLE, PERMANENT -> failed++;
                    }
                    sleep(300);
                }
            }

            profile.setLastCheckedAt(Instant.now());
            profile.setLastError(null);
            profileService.saveRuntimeState(profile);
            lastSuccessfulPoll.put(profile.getId(), now);

            String status = String.format(
                    "«%s»: найдено %d, свежих %d, новых %d, отправлено %d, ошибок %d, "
                            + "без подтверждения %d, пропущено %d",
                    profile.getName(), listings.size(), fresh.size(), newCount,
                    sent, failed, unconfirmed, skipped);
            settingsService.recordPollFinished(status);
            log.info("Poll finished in {} ms: {}", System.currentTimeMillis() - started, status);
            return new PollSummary(true, status, listings.size(), newCount, sent, failed, skipped, unconfirmed);

        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("Polling profile {} failed: {}", profile.getId(), message, e);
            try {
                profile.setLastCheckedAt(Instant.now());
                profile.setLastError(message);
                profileService.saveRuntimeState(profile);
                settingsService.recordPollFinished("Ошибка профиля «" + profile.getName() + "»: " + message);
            } catch (Exception saveError) {
                log.error("Cannot save poll error", saveError);
            }
            return new PollSummary(false, "Ошибка: " + message, 0, 0, 0, 0, 0, 0);
        } finally {
            running.set(false);
        }
    }

    private Instant windowStartFor(FilterProfile profile, int intervalMinutes, Instant now) {
        Instant since = lastSuccessfulPoll.get(profile.getId());
        if (since == null) {
            // Cold start (also after a Render restart with an empty database):
            // look only at the last interval, never at the whole history.
            return now.minusSeconds(intervalMinutes * 60L);
        }
        Instant earliest = now.minusSeconds(MAX_CATCHUP_MINUTES * 60L);
        if (since.isBefore(earliest)) {
            since = earliest;
        }
        return since.minusSeconds(WINDOW_OVERLAP_SECONDS);
    }

    private boolean isFresh(AvbyListing listing, Instant windowStart) {
        return listing.publishedAt() != null && !listing.publishedAt().isBefore(windowStart);
    }

    private boolean isPageOlderThanWindow(List<AvbyListing> listings, Instant windowStart) {
        Instant oldestEvent = null;
        for (AvbyListing listing : listings) {
            Instant eventTime = listing.eventTime();
            if (eventTime == null) {
                // Unknown ordering: keep scanning, but never send this listing.
                return false;
            }
            if (oldestEvent == null || eventTime.isBefore(oldestEvent)) {
                oldestEvent = eventTime;
            }
        }
        // Results are sorted newest first, so the oldest item on this page is
        // also the newest possible item on the next page.
        return oldestEvent != null && oldestEvent.isBefore(windowStart);
    }

    /**
     * Claim the advert globally, commit the claim, and only then call Telegram.
     *
     * <p>The claim is written before the network request. If the process crashes
     * or the request outcome is unknown, the claim remains set and the listing is
     * never sent again automatically. A claim is released only when Telegram
     * definitely rejected the request, because in that case a retry cannot
     * create a duplicate.</p>
     */
    private DeliveryOutcome deliver(SeenListing seen, AvbyListing listing, AppSettings settings) {
        seen.setTelegramClaimed(true);
        seen.setNotified(true);
        seen.setNotifyAttempts(seen.getNotifyAttempts() + 1);
        seen.setNotifyError(null);
        seenListingRepository.save(seen);

        TelegramClient.TelegramResult result = telegramClient.sendListing(listing, settings);

        switch (result.status()) {
            case SENT -> {
                seen.setTelegramSent(true);
                seen.setTelegramClaimed(true);
                seen.setNotified(true);
                seen.setNotifyError(result.partial() ? result.message() : null);
            }
            case AMBIGUOUS -> {
                // Delivery unknown: keep the claim so it is never retried.
                seen.setTelegramSent(false);
                seen.setTelegramClaimed(true);
                seen.setNotified(true);
                seen.setNotifyError("Отправка не подтверждена (повторно не отправляется): "
                        + result.message());
            }
            case RETRYABLE, PERMANENT -> {
                // Telegram definitely did not accept the message.
                seen.setTelegramClaimed(false);
                seen.setTelegramSent(false);
                seen.setNotifyError(result.message());
                boolean stopRetrying = result.status() == TelegramClient.DeliveryStatus.PERMANENT
                        || seen.getNotifyAttempts() >= MAX_NOTIFY_ATTEMPTS;
                seen.setNotified(stopRetrying);
            }
        }
        seenListingRepository.save(seen);
        return new DeliveryOutcome(result.status(), result.message(), result.partial());
    }

    private void markAlreadyClaimed(SeenListing seen, String advertId) {
        boolean delivered = seenListingRepository.existsByAdvertIdAndTelegramSentTrue(advertId);
        seen.setNotified(true);
        seen.setTelegramClaimed(true);
        seen.setTelegramSent(delivered);
        seen.setNotifyError(delivered
                ? "Уже отправлено из другого профиля"
                : "Отправка уже выполнялась из другого профиля; повторно не отправляется");
    }

    private boolean isTelegramReady(AppSettings settings) {
        return settings.isTelegramEnabled()
                && settings.getTelegramBotToken() != null
                && !settings.getTelegramBotToken().isBlank()
                && settings.getTelegramChatId() != null
                && !settings.getTelegramChatId().isBlank();
    }

    private SeenListing toEntity(Long profileId, AvbyListing listing) {
        SeenListing seen = new SeenListing();
        seen.setProfileId(profileId);
        seen.setAdvertId(listing.advertId());
        seen.setTitle(listing.title());
        seen.setUrl(listing.url());
        seen.setPriceText(listing.priceText());
        seen.setYearText(listing.yearText());
        seen.setParamsText(listing.paramsText());
        seen.setMileageText(listing.mileageText());
        seen.setLocation(listing.location());
        seen.setPublishedText(listing.publishedText());
        seen.setDescription(listing.description());
        seen.setImageUrl(listing.imageUrl());
        seen.setBadges(listing.badges());
        seen.setFirstSeenAt(Instant.now());
        return seen;
    }

    private AvbyListing fromEntity(SeenListing seen) {
        return new AvbyListing(
                seen.getAdvertId(),
                seen.getTitle(),
                seen.getUrl(),
                seen.getPriceText(),
                seen.getYearText(),
                seen.getParamsText(),
                seen.getMileageText(),
                seen.getLocation(),
                seen.getPublishedText(),
                seen.getDescription(),
                seen.getImageUrl(),
                seen.getBadges()
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record DeliveryOutcome(
            TelegramClient.DeliveryStatus status,
            String message,
            boolean partial
    ) {
    }

    public record PollSummary(
            boolean success,
            String message,
            int fetched,
            int newListings,
            int sent,
            int failed,
            int skipped,
            int unconfirmed
    ) {
    }
}
