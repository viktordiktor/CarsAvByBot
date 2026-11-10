package com.avby.avby.web;

import com.avby.avby.avby.AvbyFilterUrlBuilder;
import com.avby.avby.domain.AppSettings;
import com.avby.avby.domain.FilterProfile;
import com.avby.avby.domain.SeenListing;
import com.avby.avby.repository.SeenListingRepository;
import com.avby.avby.service.AppSettingsService;
import com.avby.avby.service.FilterProfileService;
import com.avby.avby.service.MonitorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final FilterProfileService profileService;
    private final AppSettingsService settingsService;
    private final SeenListingRepository seenListingRepository;
    private final MonitorService monitorService;
    private final AvbyFilterUrlBuilder urlBuilder;

    public DashboardController(
            FilterProfileService profileService,
            AppSettingsService settingsService,
            SeenListingRepository seenListingRepository,
            MonitorService monitorService,
            AvbyFilterUrlBuilder urlBuilder
    ) {
        this.profileService = profileService;
        this.settingsService = settingsService;
        this.seenListingRepository = seenListingRepository;
        this.monitorService = monitorService;
        this.urlBuilder = urlBuilder;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<FilterProfile> profiles = profileService.findAll();
        AppSettings settings = settingsService.get();

        Map<Long, String> profileUrls = new HashMap<>();
        Map<Long, Long> profileSeenCounts = new HashMap<>();
        for (FilterProfile profile : profiles) {
            profileUrls.put(profile.getId(), urlBuilder.build(profile, 1));
            profileSeenCounts.put(profile.getId(), seenListingRepository.countByProfileId(profile.getId()));
        }

        List<SeenListing> recent = seenListingRepository.findTop100ByOrderByFirstSeenAtDesc()
                .stream()
                .limit(10)
                .toList();

        model.addAttribute("profiles", profiles);
        model.addAttribute("settings", settings);
        model.addAttribute("profileUrls", profileUrls);
        model.addAttribute("profileSeenCounts", profileSeenCounts);
        model.addAttribute("recent", recent);
        model.addAttribute("totalSeen", seenListingRepository.count());
        model.addAttribute("pendingSeen", seenListingRepository.countByNotifiedFalse());
        model.addAttribute("lastDaySeen", seenListingRepository.countByFirstSeenAtAfter(
                Instant.now().minus(24, ChronoUnit.HOURS)));
        model.addAttribute("telegramReady", telegramReady(settings));
        return "dashboard";
    }

    @PostMapping("/profiles/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirect) {
        profileService.toggle(id);
        redirect.addFlashAttribute("message", "Статус профиля изменён");
        return "redirect:/";
    }

    @PostMapping("/profiles/{id}/poll")
    public String poll(@PathVariable Long id, RedirectAttributes redirect) {
        Thread.startVirtualThread(() -> monitorService.pollProfile(id, true));
        redirect.addFlashAttribute("message", "Проверка профиля запущена в фоне. Обновите страницу через минуту.");
        return "redirect:/";
    }

    @PostMapping("/poll-all")
    public String pollAll(RedirectAttributes redirect) {
        Thread.startVirtualThread(monitorService::pollAllNow);
        redirect.addFlashAttribute("message", "Проверка всех включённых профилей запущена в фоне.");
        return "redirect:/";
    }

    @PostMapping("/profiles/{id}/reset")
    public String reset(@PathVariable Long id, RedirectAttributes redirect) {
        profileService.resetHistory(id);
        redirect.addFlashAttribute("message", "История профиля очищена");
        return "redirect:/";
    }

    @PostMapping("/profiles/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        profileService.delete(id);
        redirect.addFlashAttribute("message", "Профиль удалён");
        return "redirect:/";
    }

    private boolean telegramReady(AppSettings settings) {
        return settings.isTelegramEnabled()
                && settings.getTelegramBotToken() != null && !settings.getTelegramBotToken().isBlank()
                && settings.getTelegramChatId() != null && !settings.getTelegramChatId().isBlank();
    }
}
