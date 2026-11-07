package com.avby.avby.web;

import com.avby.avby.avby.AvbyFilterUrlBuilder;
import com.avby.avby.domain.FilterProfile;
import com.avby.avby.service.FilterProfileService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final FilterProfileService profileService;
    private final AvbyFilterUrlBuilder urlBuilder;

    public ProfileController(FilterProfileService profileService, AvbyFilterUrlBuilder urlBuilder) {
        this.profileService = profileService;
        this.urlBuilder = urlBuilder;
    }

    @GetMapping("/profiles/new")
    public String createForm(Model model) {
        ProfileForm form = new ProfileForm();
        form.setName("Срочные объявления");
        form.setKeyword("срочно");
        form.setSellerType(1);
        model.addAttribute("form", form);
        addOptions(model);
        return "profile-form";
    }

    @GetMapping("/profiles/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FilterProfile profile = profileService.get(id);
        model.addAttribute("form", ProfileForm.from(profile));
        model.addAttribute("profile", profile);
        model.addAttribute("previewUrl", urlBuilder.build(profile, 1));
        addOptions(model);
        return "profile-form";
    }

    @PostMapping("/profiles")
    public String save(
            @Valid @ModelAttribute("form") ProfileForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes redirect
    ) {
        validateRanges(form, binding);
        if (binding.hasErrors()) {
            addOptions(model);
            return "profile-form";
        }

        FilterProfile saved = profileService.save(form.toProfile());
        redirect.addFlashAttribute("message", "Профиль «" + saved.getName() + "» сохранён");
        return "redirect:/";
    }

    private void addOptions(Model model) {
        model.addAttribute("sellerTypes", FilterOptions.SELLER_TYPES);
        model.addAttribute("transmissions", FilterOptions.TRANSMISSION_TYPES);
        model.addAttribute("bodies", FilterOptions.BODY_TYPES);
        model.addAttribute("engines", FilterOptions.ENGINE_TYPES);
        model.addAttribute("drives", FilterOptions.DRIVE_TYPES);
        model.addAttribute("brands", FilterOptions.BRANDS);
        model.addAttribute("regions", FilterOptions.REGIONS);
        model.addAttribute("colors", FilterOptions.COLORS);
        model.addAttribute("years", FilterOptions.years());
    }

    private void validateRanges(ProfileForm form, BindingResult binding) {
        if (form.getPriceMinUsd() != null && form.getPriceMaxUsd() != null
                && form.getPriceMinUsd() > form.getPriceMaxUsd()) {
            binding.rejectValue("priceMaxUsd", "range", "Максимальная цена меньше минимальной");
        }
        if (form.getYearMin() != null && form.getYearMax() != null
                && form.getYearMin() > form.getYearMax()) {
            binding.rejectValue("yearMax", "range", "Максимальный год меньше минимального");
        }
        if (form.getMileageMinKm() != null && form.getMileageMaxKm() != null
                && form.getMileageMinKm() > form.getMileageMaxKm()) {
            binding.rejectValue("mileageMaxKm", "range", "Максимальный пробег меньше минимального");
        }
    }
}
