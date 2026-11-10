package com.avby.avby.avby;

import com.avby.avby.domain.FilterProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a cars.av.by/filter URL from a saved profile.
 */
@Component
public class AvbyFilterUrlBuilder {

    private final String baseUrl;

    public AvbyFilterUrlBuilder(@Value("${avby.base-url:https://cars.av.by}") String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public String build(FilterProfile profile, int page) {
        List<String> params = new ArrayList<>();

        add(params, "seller_type[0]", profile.getSellerType());
        add(params, "description", blankToNull(profile.getKeyword()));
        add(params, "price_usd[min]", profile.getPriceMinUsd());
        add(params, "price_usd[max]", profile.getPriceMaxUsd());
        if (profile.getPriceMinUsd() != null || profile.getPriceMaxUsd() != null) {
            add(params, "price_currency", 2); // USD
        }
        add(params, "year[min]", profile.getYearMin());
        add(params, "year[max]", profile.getYearMax());
        add(params, "mileage_km[min]", profile.getMileageMinKm());
        add(params, "mileage_km[max]", profile.getMileageMaxKm());
        addMulti(params, "transmission_type", profile.getTransmissionType());
        addMulti(params, "body_type", profile.getBodyType());
        addMulti(params, "engine_type", profile.getEngineType());
        addMulti(params, "drive_type", profile.getDriveType());
        add(params, "brands[0][brand]", blankToNull(profile.getBrandId()));
        add(params, "place_region[0]", blankToNull(profile.getRegion()));
        add(params, "color[0]", blankToNull(profile.getColor()));

        // "Новые объявления": av.by's default "актуальные" mixes in promoted
        // listings, so a freshly published advert can be pushed off page 1.
        String extra = blankToNull(profile.getExtraQuery());
        if (extra == null || !hasSort(extra)) {
            add(params, "sort", 4);
        }

        if (page > 1) {
            add(params, "page", page);
        }

        if (extra != null) {
            if (extra.startsWith("?") || extra.startsWith("&")) {
                extra = extra.substring(1);
            }
            params.add(extra);
        }

        String query = String.join("&", params);
        return baseUrl + "/filter" + (query.isEmpty() ? "" : "?" + query);
    }

    private boolean hasSort(String extra) {
        return extra.startsWith("sort=")
                || extra.contains("&sort=")
                || extra.contains("?sort=");
    }

    private void add(List<String> params, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return;
        }
        params.add(name + "=" + encode(text));
    }

    private void addMulti(List<String> params, String name, String rawValues) {
        String value = blankToNull(rawValues);
        if (value == null) {
            return;
        }
        String[] parts = value.split(",");
        int index = 0;
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty()) {
                params.add(name + "[" + index + "]=" + encode(item));
                index++;
            }
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://cars.av.by";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
