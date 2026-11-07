package com.avby.avby.web;

import com.avby.avby.domain.FilterProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Form-backing object for the profile editor. Multi-value av.by filters are
 * edited as checkbox groups and stored in the entity as comma-separated ids.
 */
public class ProfileForm {

    private Long id;

    @NotBlank(message = "Укажите название профиля")
    @Size(max = 120, message = "Не больше 120 символов")
    private String name;

    private boolean enabled = true;

    @Size(max = 200, message = "Не больше 200 символов")
    private String keyword = "срочно";

    private Integer sellerType = 1;

    private Integer priceMinUsd;
    private Integer priceMaxUsd;

    private Integer yearMin;
    private Integer yearMax;

    private Integer mileageMinKm;
    private Integer mileageMaxKm;

    private List<String> transmissionType = new ArrayList<>();
    private List<String> bodyType = new ArrayList<>();
    private List<String> engineType = new ArrayList<>();
    private List<String> driveType = new ArrayList<>();

    @Size(max = 50)
    private String brandId;

    @Size(max = 50)
    private String modelId;

    @Size(max = 50)
    private String region;

    @Size(max = 50)
    private String color;

    @Size(max = 2000, message = "Не больше 2000 символов")
    private String extraQuery;

    private int pagesToScan = 1;

    private boolean sendExistingOnFirstRun = false;

    private Integer pollIntervalMinutes;

    public static ProfileForm from(FilterProfile profile) {
        ProfileForm form = new ProfileForm();
        form.setId(profile.getId());
        form.setName(profile.getName());
        form.setEnabled(profile.isEnabled());
        form.setKeyword(profile.getKeyword());
        form.setSellerType(profile.getSellerType());
        form.setPriceMinUsd(profile.getPriceMinUsd());
        form.setPriceMaxUsd(profile.getPriceMaxUsd());
        form.setYearMin(profile.getYearMin());
        form.setYearMax(profile.getYearMax());
        form.setMileageMinKm(profile.getMileageMinKm());
        form.setMileageMaxKm(profile.getMileageMaxKm());
        form.setTransmissionType(split(profile.getTransmissionType()));
        form.setBodyType(split(profile.getBodyType()));
        form.setEngineType(split(profile.getEngineType()));
        form.setDriveType(split(profile.getDriveType()));
        form.setBrandId(profile.getBrandId());
        form.setModelId(profile.getModelId());
        form.setRegion(profile.getRegion());
        form.setColor(profile.getColor());
        form.setExtraQuery(profile.getExtraQuery());
        form.setPagesToScan(profile.getPagesToScan());
        form.setSendExistingOnFirstRun(profile.isSendExistingOnFirstRun());
        form.setPollIntervalMinutes(profile.getPollIntervalMinutes());
        return form;
    }

    public FilterProfile toProfile() {
        FilterProfile profile = new FilterProfile();
        profile.setId(id);
        profile.setName(name == null ? null : name.trim());
        profile.setEnabled(enabled);
        profile.setKeyword(keyword);
        profile.setSellerType(sellerType);
        profile.setPriceMinUsd(priceMinUsd);
        profile.setPriceMaxUsd(priceMaxUsd);
        profile.setYearMin(yearMin);
        profile.setYearMax(yearMax);
        profile.setMileageMinKm(mileageMinKm);
        profile.setMileageMaxKm(mileageMaxKm);
        profile.setTransmissionType(join(transmissionType));
        profile.setBodyType(join(bodyType));
        profile.setEngineType(join(engineType));
        profile.setDriveType(join(driveType));
        profile.setBrandId(brandId);
        profile.setModelId(modelId);
        profile.setRegion(region);
        profile.setColor(color);
        profile.setExtraQuery(extraQuery);
        profile.setPagesToScan(pagesToScan);
        profile.setSendExistingOnFirstRun(sendExistingOnFirstRun);
        profile.setPollIntervalMinutes(pollIntervalMinutes);
        return profile;
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getSellerType() {
        return sellerType;
    }

    public void setSellerType(Integer sellerType) {
        this.sellerType = sellerType;
    }

    public Integer getPriceMinUsd() {
        return priceMinUsd;
    }

    public void setPriceMinUsd(Integer priceMinUsd) {
        this.priceMinUsd = priceMinUsd;
    }

    public Integer getPriceMaxUsd() {
        return priceMaxUsd;
    }

    public void setPriceMaxUsd(Integer priceMaxUsd) {
        this.priceMaxUsd = priceMaxUsd;
    }

    public Integer getYearMin() {
        return yearMin;
    }

    public void setYearMin(Integer yearMin) {
        this.yearMin = yearMin;
    }

    public Integer getYearMax() {
        return yearMax;
    }

    public void setYearMax(Integer yearMax) {
        this.yearMax = yearMax;
    }

    public Integer getMileageMinKm() {
        return mileageMinKm;
    }

    public void setMileageMinKm(Integer mileageMinKm) {
        this.mileageMinKm = mileageMinKm;
    }

    public Integer getMileageMaxKm() {
        return mileageMaxKm;
    }

    public void setMileageMaxKm(Integer mileageMaxKm) {
        this.mileageMaxKm = mileageMaxKm;
    }

    public List<String> getTransmissionType() {
        return transmissionType;
    }

    public void setTransmissionType(List<String> transmissionType) {
        this.transmissionType = transmissionType == null ? new ArrayList<>() : transmissionType;
    }

    public List<String> getBodyType() {
        return bodyType;
    }

    public void setBodyType(List<String> bodyType) {
        this.bodyType = bodyType == null ? new ArrayList<>() : bodyType;
    }

    public List<String> getEngineType() {
        return engineType;
    }

    public void setEngineType(List<String> engineType) {
        this.engineType = engineType == null ? new ArrayList<>() : engineType;
    }

    public List<String> getDriveType() {
        return driveType;
    }

    public void setDriveType(List<String> driveType) {
        this.driveType = driveType == null ? new ArrayList<>() : driveType;
    }

    public String getBrandId() {
        return brandId;
    }

    public void setBrandId(String brandId) {
        this.brandId = brandId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getExtraQuery() {
        return extraQuery;
    }

    public void setExtraQuery(String extraQuery) {
        this.extraQuery = extraQuery;
    }

    public int getPagesToScan() {
        return pagesToScan;
    }

    public void setPagesToScan(int pagesToScan) {
        this.pagesToScan = pagesToScan;
    }

    public boolean isSendExistingOnFirstRun() {
        return sendExistingOnFirstRun;
    }

    public void setSendExistingOnFirstRun(boolean sendExistingOnFirstRun) {
        this.sendExistingOnFirstRun = sendExistingOnFirstRun;
    }

    public Integer getPollIntervalMinutes() {
        return pollIntervalMinutes;
    }

    public void setPollIntervalMinutes(Integer pollIntervalMinutes) {
        this.pollIntervalMinutes = pollIntervalMinutes;
    }
}
