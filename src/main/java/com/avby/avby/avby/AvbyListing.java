package com.avby.avby.avby;

import java.time.Instant;
import java.util.List;

/**
 * A single listing parsed from the av.by filter page.
 *
 * <p>{@code publishedAt} is the exact publication timestamp from the page's
 * embedded JSON. {@code renewedAt} is set when the seller bumps the listing.
 * They are {@code null} only when av.by changed its markup; in that case the
 * monitor skips the listing instead of risking a spam of old adverts.</p>
 */
public record AvbyListing(
        String advertId,
        String title,
        String url,
        String priceText,
        String yearText,
        String paramsText,
        String mileageText,
        String location,
        String publishedText,
        String description,
        String imageUrl,
        String badges,
        Instant publishedAt,
        Instant renewedAt
) {
    public AvbyListing(
            String advertId,
            String title,
            String url,
            String priceText,
            String yearText,
            String paramsText,
            String mileageText,
            String location,
            String publishedText,
            String description,
            String imageUrl,
            String badges
    ) {
        this(advertId, title, url, priceText, yearText, paramsText, mileageText,
                location, publishedText, description, imageUrl, badges, null, null);
    }

    /**
     * Timestamp used by av.by's "newest first" sorting: the bump time when
     * present, otherwise the publication time.
     */
    public Instant eventTime() {
        if (publishedAt == null) {
            return renewedAt;
        }
        if (renewedAt == null) {
            return publishedAt;
        }
        return publishedAt.isAfter(renewedAt) ? publishedAt : renewedAt;
    }

    public String shortDescription(int maxLength) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String oneLine = description.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    public static List<AvbyListing> emptyList() {
        return List.of();
    }
}
