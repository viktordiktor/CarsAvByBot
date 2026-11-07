package com.avby.avby.service;

import com.avby.avby.domain.FilterProfile;
import com.avby.avby.repository.FilterProfileRepository;
import com.avby.avby.repository.SeenListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FilterProfileService {

    private final FilterProfileRepository profileRepository;
    private final SeenListingRepository seenListingRepository;

    public FilterProfileService(
            FilterProfileRepository profileRepository,
            SeenListingRepository seenListingRepository
    ) {
        this.profileRepository = profileRepository;
        this.seenListingRepository = seenListingRepository;
    }

    public List<FilterProfile> findAll() {
        return profileRepository.findAllByOrderByIdAsc();
    }

    public List<FilterProfile> findEnabled() {
        return profileRepository.findByEnabledTrueOrderByIdAsc();
    }

    public FilterProfile get(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль не найден"));
    }

    public FilterProfile newProfile() {
        return new FilterProfile();
    }

    @Transactional
    public FilterProfile save(FilterProfile form) {
        FilterProfile target = form.getId() == null ? new FilterProfile() : get(form.getId());
        copyEditableFields(form, target);
        return profileRepository.save(target);
    }

    @Transactional
    public void toggle(Long id) {
        FilterProfile profile = get(id);
        profile.setEnabled(!profile.isEnabled());
        profileRepository.save(profile);
    }

    @Transactional
    public void delete(Long id) {
        FilterProfile profile = get(id);
        seenListingRepository.deleteByProfileId(profile.getId());
        profileRepository.delete(profile);
    }

    @Transactional
    public void resetHistory(Long id) {
        FilterProfile profile = get(id);
        seenListingRepository.deleteByProfileId(profile.getId());
        profile.setBaselineDone(false);
        profile.setLastCheckedAt(null);
        profile.setLastError(null);
        profileRepository.save(profile);
    }

    @Transactional
    public FilterProfile saveRuntimeState(FilterProfile profile) {
        return profileRepository.save(profile);
    }

    private void copyEditableFields(FilterProfile form, FilterProfile target) {
        target.setName(trim(form.getName()));
        target.setEnabled(form.isEnabled());
        target.setKeyword(trimToNull(form.getKeyword()));
        target.setSellerType(form.getSellerType());
        target.setPriceMinUsd(nonNegative(form.getPriceMinUsd()));
        target.setPriceMaxUsd(nonNegative(form.getPriceMaxUsd()));
        target.setYearMin(validYear(form.getYearMin()));
        target.setYearMax(validYear(form.getYearMax()));
        target.setMileageMinKm(nonNegative(form.getMileageMinKm()));
        target.setMileageMaxKm(nonNegative(form.getMileageMaxKm()));
        target.setTransmissionType(trimToNull(form.getTransmissionType()));
        target.setBodyType(trimToNull(form.getBodyType()));
        target.setEngineType(trimToNull(form.getEngineType()));
        target.setDriveType(trimToNull(form.getDriveType()));
        target.setBrandId(trimToNull(form.getBrandId()));
        target.setModelId(trimToNull(form.getModelId()));
        target.setRegion(trimToNull(form.getRegion()));
        target.setColor(trimToNull(form.getColor()));
        target.setExtraQuery(trimToNull(form.getExtraQuery()));
        target.setPagesToScan(Math.max(1, Math.min(10, form.getPagesToScan())));
        target.setSendExistingOnFirstRun(form.isSendExistingOnFirstRun());
        target.setPollIntervalMinutes(form.getPollIntervalMinutes() == null
                ? null
                : Math.max(1, form.getPollIntervalMinutes()));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer nonNegative(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, value);
    }

    private Integer validYear(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 1950 || value > 2100) {
            return null;
        }
        return value;
    }
}
