package com.avby.avby.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A saved av.by search. The user can create several profiles (for example
 * "дешёвые срочные до 4000$" and "внедорожники 2015+").
 */
@Entity
@Table(name = "filter_profiles")
@Getter
@Setter
@NoArgsConstructor
public class FilterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Укажите название профиля")
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Text search in the listing description. av.by parameter: description. */
    @Size(max = 200)
    @Column(length = 200)
    private String keyword = "срочно";

    /** 1 = private sellers, 2 = companies/dealers, null = all. */
    private Integer sellerType = 1;

    private Integer priceMinUsd;
    private Integer priceMaxUsd;

    private Integer yearMin;
    private Integer yearMax;

    private Integer mileageMinKm;
    private Integer mileageMaxKm;

    /** Comma-separated av.by ids, e.g. "1,3,4" for all automatic gearboxes. */
    @Size(max = 100)
    @Column(length = 100)
    private String transmissionType;

    @Size(max = 100)
    @Column(length = 100)
    private String bodyType;

    @Size(max = 100)
    @Column(length = 100)
    private String engineType;

    @Size(max = 100)
    @Column(length = 100)
    private String driveType;

    /** Optional av.by brand id. */
    @Size(max = 50)
    @Column(length = 50)
    private String brandId;

    /** Optional av.by model id. */
    @Size(max = 50)
    @Column(length = 50)
    private String modelId;

    /** Optional av.by region id. */
    @Size(max = 50)
    @Column(length = 50)
    private String region;

    /** Optional av.by colour id. */
    @Size(max = 50)
    @Column(length = 50)
    private String color;

    /**
     * Escape hatch for any additional av.by query parameters.
     * Example: {@code condition[4]=5&has_photo360=1&sort=4}
     */
    @Size(max = 2000)
    @Column(length = 2000)
    private String extraQuery;

    /** How many result pages (25 listings each) to inspect per poll. */
    private int pagesToScan = 1;

    /** If true, the first poll sends all matching listings instead of only new ones. */
    private boolean sendExistingOnFirstRun = false;

    /** Set after the first successful poll so old listings are not re-sent. */
    private boolean baselineDone = false;

    /** Optional per-profile polling interval override. */
    private Integer pollIntervalMinutes;

    private Instant lastCheckedAt;

    @Size(max = 2000)
    @Column(length = 2000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getLastCheckedFormatted() {
        if (lastCheckedAt == null) {
            return "ещё не проверялся";
        }
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(lastCheckedAt);
    }

    public String getFiltersSummary() {
        StringBuilder summary = new StringBuilder();
        if (keyword != null && !keyword.isBlank()) {
            summary.append("«").append(keyword).append("»");
        }
        if (sellerType != null) {
            summary.append(summary.isEmpty() ? "" : " · ")
                    .append(sellerType == 1 ? "частные" : "компании");
        }
        if (priceMinUsd != null || priceMaxUsd != null) {
            summary.append(summary.isEmpty() ? "" : " · ").append("цена ");
            summary.append(priceMinUsd == null ? "0" : priceMinUsd).append("–");
            summary.append(priceMaxUsd == null ? "∞" : priceMaxUsd).append(" $");
        }
        if (yearMin != null || yearMax != null) {
            summary.append(summary.isEmpty() ? "" : " · ").append("год ");
            summary.append(yearMin == null ? "…" : yearMin).append("–");
            summary.append(yearMax == null ? "…" : yearMax);
        }
        if (mileageMinKm != null || mileageMaxKm != null) {
            summary.append(summary.isEmpty() ? "" : " · ").append("пробег ");
            summary.append(mileageMinKm == null ? "0" : mileageMinKm).append("–");
            summary.append(mileageMaxKm == null ? "∞" : mileageMaxKm).append(" км");
        }
        return summary.toString();
    }
}
