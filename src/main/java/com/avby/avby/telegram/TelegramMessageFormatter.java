package com.avby.avby.telegram;

import com.avby.avby.avby.AvbyListing;
import org.springframework.stereotype.Component;

@Component
public class TelegramMessageFormatter {

    public String format(AvbyListing listing, int maxLength) {
        int descriptionLimit = Math.max(100, Math.min(700, maxLength - 450));
        StringBuilder text = new StringBuilder();

        String title = listing.title() == null || listing.title().isBlank()
                ? "Объявление на av.by"
                : listing.title();
        text.append("🚗 <b>").append(escape(title)).append("</b>\n");

        if (hasText(listing.priceText())) {
            text.append("💰 <b>").append(escape(listing.priceText())).append("</b>\n");
        }

        String mainParams = joinNonBlank(" • ", listing.yearText(), listing.paramsText());
        if (!mainParams.isBlank()) {
            text.append("📅 ").append(escape(mainParams)).append("\n");
        }

        if (hasText(listing.mileageText())) {
            text.append("🛣 ").append(escape(listing.mileageText())).append("\n");
        }

        String placeAndDate = joinNonBlank(" • ", listing.location(), listing.publishedText());
        if (!placeAndDate.isBlank()) {
            text.append("📍 ").append(escape(placeAndDate)).append("\n");
        }

        if (hasText(listing.badges())) {
            text.append("🏷 ").append(escape(listing.badges())).append("\n");
        }

        String description = listing.shortDescription(descriptionLimit);
        if (!description.isBlank()) {
            text.append("\n").append(escape(description)).append("\n");
        }

        text.append("\n<a href=\"").append(escapeAttribute(listing.url()))
                .append("\">Открыть объявление на av.by</a>");

        return text.toString();
    }

    private String joinNonBlank(String delimiter, String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (hasText(value)) {
                if (!result.isEmpty()) {
                    result.append(delimiter);
                }
                result.append(value.trim());
            }
        }
        return result.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }
}
