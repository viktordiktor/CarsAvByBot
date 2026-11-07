package com.avby.avby.avby;

import java.util.List;

/**
 * A single listing parsed from the av.by filter page.
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
        String badges
) {
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
