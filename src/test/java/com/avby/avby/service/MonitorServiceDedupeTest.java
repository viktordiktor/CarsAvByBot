package com.avby.avby.service;

import com.avby.avby.avby.AvbyClient;
import com.avby.avby.avby.AvbyListing;
import com.avby.avby.domain.FilterProfile;
import com.avby.avby.domain.SeenListing;
import com.avby.avby.repository.AppSettingsRepository;
import com.avby.avby.repository.FilterProfileRepository;
import com.avby.avby.repository.SeenListingRepository;
import com.avby.avby.telegram.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MonitorServiceDedupeTest {

    private static final String ADVERT_ID = "1001";

    @MockitoBean
    private AvbyClient avbyClient;

    @MockitoBean
    private TelegramClient telegramClient;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private FilterProfileRepository profileRepository;

    @Autowired
    private SeenListingRepository seenListingRepository;

    @Autowired
    private AppSettingsRepository appSettingsRepository;

    @Autowired
    private AppSettingsService settingsService;

    private AvbyListing listing;

    @BeforeEach
    void setUp() {
        seenListingRepository.deleteAll();
        profileRepository.deleteAll();
        appSettingsRepository.deleteAll();
        Mockito.reset(avbyClient, telegramClient);

        settingsService.update("123:TEST", "42", true, false, 5, 300);

        listing = new AvbyListing(
                ADVERT_ID,
                "BMW X5",
                "https://cars.av.by/bmw/x5/" + ADVERT_ID,
                "10 000 $",
                "2015 г.",
                "автомат, 3,0 л, дизель",
                "200 000 км",
                "Минск",
                "сегодня",
                "Срочно, торг",
                null,
                null
        );

        when(avbyClient.fetchPage(any(), anyInt()))
                .thenReturn(new AvbyClient.PageResult(List.of(listing), 1, "https://cars.av.by/filter"));
        when(telegramClient.sendListing(any(), any()))
                .thenReturn(TelegramClient.TelegramResult.sent("OK"));
    }

    @Test
    void sameAdvertIsSentOnlyOnceAcrossProfiles() {
        FilterProfile first = newProfile("Первый", true);
        FilterProfile second = newProfile("Второй", true);

        monitorService.pollProfile(first.getId(), true);
        monitorService.pollProfile(second.getId(), true);

        verify(telegramClient, times(1)).sendListing(any(), any());
        assertThat(seenListingRepository.existsByAdvertIdAndTelegramSentTrue(ADVERT_ID)).isTrue();

        List<SeenListing> rows = seenListingRepository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(SeenListing::isNotified);
        assertThat(rows).allMatch(SeenListing::isTelegramClaimed);
        assertThat(rows).allMatch(SeenListing::isTelegramSent);
    }

    @Test
    void repeatedPollDoesNotResendTheSameListing() {
        FilterProfile profile = newProfile("Профиль", true);

        monitorService.pollProfile(profile.getId(), true);
        monitorService.pollProfile(profile.getId(), true);

        verify(telegramClient, times(1)).sendListing(any(), any());
        assertThat(seenListingRepository.countByProfileId(profile.getId())).isEqualTo(1);
    }

    @Test
    void firstPollIsBaselineAndDoesNotSend() {
        FilterProfile profile = newProfile("Профиль", false);

        monitorService.pollProfile(profile.getId(), true);

        verify(telegramClient, never()).sendListing(any(), any());
        SeenListing row = seenListingRepository
                .findByProfileIdAndAdvertId(profile.getId(), ADVERT_ID)
                .orElseThrow();
        assertThat(row.isNotified()).isTrue();
        assertThat(row.isTelegramClaimed()).isFalse();
        assertThat(row.isTelegramSent()).isFalse();
    }

    @Test
    void retryableFailureIsRetriedOnTheNextPoll() {
        when(telegramClient.sendListing(any(), any()))
                .thenReturn(TelegramClient.TelegramResult.failed(
                        "Too Many Requests", TelegramClient.DeliveryStatus.RETRYABLE))
                .thenReturn(TelegramClient.TelegramResult.sent("OK"));
        FilterProfile profile = newProfile("Профиль", true);

        monitorService.pollProfile(profile.getId(), true);

        SeenListing row = seenListingRepository
                .findByProfileIdAndAdvertId(profile.getId(), ADVERT_ID)
                .orElseThrow();
        assertThat(row.isNotified()).isFalse();
        assertThat(row.isTelegramClaimed()).isFalse();
        assertThat(row.isTelegramSent()).isFalse();
        assertThat(row.getNotifyAttempts()).isEqualTo(1);
        verify(telegramClient, times(1)).sendListing(any(), any());

        monitorService.pollProfile(profile.getId(), true);

        row = seenListingRepository
                .findByProfileIdAndAdvertId(profile.getId(), ADVERT_ID)
                .orElseThrow();
        assertThat(row.isNotified()).isTrue();
        assertThat(row.isTelegramClaimed()).isTrue();
        assertThat(row.isTelegramSent()).isTrue();
        assertThat(row.getNotifyAttempts()).isEqualTo(2);
        verify(telegramClient, times(2)).sendListing(any(), any());
    }

    @Test
    void ambiguousFailureIsNeverRetriedAutomatically() {
        when(telegramClient.sendListing(any(), any()))
                .thenReturn(TelegramClient.TelegramResult.failed(
                        "read timeout", TelegramClient.DeliveryStatus.AMBIGUOUS));
        FilterProfile first = newProfile("Первый", true);
        FilterProfile second = newProfile("Второй", true);

        monitorService.pollProfile(first.getId(), true);

        SeenListing row = seenListingRepository
                .findByProfileIdAndAdvertId(first.getId(), ADVERT_ID)
                .orElseThrow();
        assertThat(row.isNotified()).isTrue();
        assertThat(row.isTelegramClaimed()).isTrue();
        assertThat(row.isTelegramSent()).isFalse();
        assertThat(row.getNotifyError()).contains("не подтверждена");

        monitorService.pollProfile(first.getId(), true);
        monitorService.pollProfile(second.getId(), true);

        verify(telegramClient, times(1)).sendListing(any(), any());
    }

    @Test
    void permanentFailureIsNotRetriedAutomatically() {
        when(telegramClient.sendListing(any(), any()))
                .thenReturn(TelegramClient.TelegramResult.failed(
                        "chat not found", TelegramClient.DeliveryStatus.PERMANENT));
        FilterProfile profile = newProfile("Профиль", true);

        monitorService.pollProfile(profile.getId(), true);
        monitorService.pollProfile(profile.getId(), true);

        verify(telegramClient, times(1)).sendListing(any(), any());
        SeenListing row = seenListingRepository
                .findByProfileIdAndAdvertId(profile.getId(), ADVERT_ID)
                .orElseThrow();
        assertThat(row.isNotified()).isTrue();
        assertThat(row.isTelegramClaimed()).isFalse();
        assertThat(row.isTelegramSent()).isFalse();
    }

    private FilterProfile newProfile(String name, boolean baselineDone) {
        FilterProfile profile = new FilterProfile();
        profile.setName(name);
        profile.setEnabled(true);
        profile.setPagesToScan(1);
        profile.setBaselineDone(baselineDone);
        return profileRepository.save(profile);
    }
}
