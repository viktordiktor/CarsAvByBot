package com.avby.avby.web;

import com.avby.avby.domain.FilterProfile;
import com.avby.avby.domain.SeenListing;
import com.avby.avby.repository.SeenListingRepository;
import com.avby.avby.service.FilterProfileService;
import com.avby.avby.service.MonitorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ListingsController {

    private final SeenListingRepository seenListingRepository;
    private final FilterProfileService profileService;
    private final MonitorService monitorService;

    public ListingsController(
            SeenListingRepository seenListingRepository,
            FilterProfileService profileService,
            MonitorService monitorService
    ) {
        this.seenListingRepository = seenListingRepository;
        this.profileService = profileService;
        this.monitorService = monitorService;
    }

    @GetMapping("/listings")
    public String listings(@RequestParam(required = false) Long profileId, Model model) {
        List<FilterProfile> profiles = profileService.findAll();
        List<SeenListing> listings = profileId == null
                ? seenListingRepository.findTop100ByOrderByFirstSeenAtDesc()
                : seenListingRepository.findByProfileIdOrderByFirstSeenAtDesc(profileId);

        model.addAttribute("profiles", profiles);
        model.addAttribute("listings", listings);
        model.addAttribute("selectedProfileId", profileId);
        return "listings";
    }

    @PostMapping("/listings/{id}/resend")
    public String resend(@PathVariable Long id, RedirectAttributes redirect) {
        MonitorService.PollSummary result = monitorService.resend(id);
        redirect.addFlashAttribute(result.success() ? "message" : "error", result.message());
        return "redirect:/listings";
    }
}
