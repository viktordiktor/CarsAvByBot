package com.avby.avby.avby;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the server-rendered av.by filter page.
 *
 * <p>The site does not expose a stable public JSON API for this search, so we
 * parse the same HTML that a browser receives. All selectors are kept in this
 * class to make future markup changes easy to fix.</p>
 */
@Component
public class AvbyParser {

    private static final Logger log = LoggerFactory.getLogger(AvbyParser.class);
    private static final String CARS_BASE_URL = "https://cars.av.by";
    private static final DateTimeFormatter AVBY_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .appendOffset("+HHMM", "Z")
            .toFormatter();

    public List<AvbyListing> parse(String html) {
        Document document = Jsoup.parse(html);
        Map<String, TimestampedAdvert> timestamps = parseEmbeddedTimestamps(document);
        Elements cards = document.select("div.listing-item");
        Map<String, AvbyListing> byId = new LinkedHashMap<>();

        for (Element card : cards) {
            AvbyListing listing = parseCard(card, timestamps);
            if (listing != null) {
                byId.putIfAbsent(listing.advertId(), listing);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private AvbyListing parseCard(Element card, Map<String, TimestampedAdvert> timestamps) {
        Element link = card.selectFirst("a.listing-item__link");
        if (link == null) {
            return null;
        }

        String href = link.attr("href").trim();
        if (href.isEmpty()) {
            return null;
        }

        String advertId = extractAdvertId(href);
        if (advertId == null) {
            return null;
        }

        Elements params = card.select(".listing-item__params > div");
        String yearText = params.size() > 0 ? clean(params.get(0).text()) : "";
        String paramsText = params.size() > 1 ? clean(params.get(1).text()) : "";
        String mileageText = params.size() > 2 ? clean(params.get(2).text()) : "";

        Element price = card.selectFirst(".listing-item__price-primary");
        Element description = card.selectFirst(".listing-item__message");
        Element location = card.selectFirst(".listing-item__location");
        Element date = card.selectFirst(".listing-item__date");

        String imageUrl = "";
        Element image = card.selectFirst(".listing-item__photo img");
        if (image != null) {
            imageUrl = image.attr("data-src");
            if (imageUrl.isBlank()) {
                imageUrl = image.attr("src");
            }
            if (imageUrl.startsWith("data:")) {
                imageUrl = "";
            }
        }

        List<String> badgeLabels = new ArrayList<>();
        for (Element badge : card.select(".listing-item__badges .badge__label")) {
            String text = clean(badge.text());
            if (!text.isBlank()) {
                badgeLabels.add(text);
            }
        }

        TimestampedAdvert times = timestamps.get(advertId);
        return new AvbyListing(
                advertId,
                clean(link.text()),
                toAbsoluteUrl(href),
                price == null ? "" : clean(price.text()),
                yearText,
                paramsText,
                mileageText,
                location == null ? "" : clean(location.text()),
                date == null ? "" : clean(date.text()),
                description == null ? "" : clean(description.text()),
                imageUrl,
                String.join(" · ", badgeLabels),
                times == null ? null : times.publishedAt(),
                times == null ? null : times.renewedAt()
        );
    }

    /**
     * av.by renders a small Next.js state blob that contains exact
     * {@code publishedAt} / {@code renewedAt} timestamps for every card. The
     * visible date only says "2 минуты назад", which is not precise enough for
     * a strict polling window.
     */
    private Map<String, TimestampedAdvert> parseEmbeddedTimestamps(Document document) {
        Element script = document.selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            return Map.of();
        }
        String json = script.data();
        if (json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, TimestampedAdvert> result = new HashMap<>();
            collectTimestamps(JsonParser.parseString(json), result);
            return result;
        } catch (RuntimeException e) {
            log.warn("Cannot parse av.by __NEXT_DATA__: {}", e.getMessage());
            return Map.of();
        }
    }

    private void collectTimestamps(JsonElement element, Map<String, TimestampedAdvert> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectTimestamps(item, out);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement id = object.get("id");
        JsonElement published = object.get("publishedAt");
        if (id != null && id.isJsonPrimitive() && published != null && published.isJsonPrimitive()) {
            Instant publishedAt = parseInstant(published.getAsString());
            if (publishedAt != null) {
                JsonElement renewed = object.get("renewedAt");
                Instant renewedAt = renewed != null && renewed.isJsonPrimitive()
                        ? parseInstant(renewed.getAsString())
                        : null;
                out.putIfAbsent(id.getAsString(), new TimestampedAdvert(publishedAt, renewedAt));
            }
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectTimestamps(entry.getValue(), out);
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.endsWith("Z")) {
                return Instant.parse(text);
            }
            if (text.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(text).toInstant();
            }
            return OffsetDateTime.parse(text, AVBY_TIMESTAMP).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Cannot parse av.by timestamp '{}': {}", text, e.getMessage());
            return null;
        }
    }

    private String extractAdvertId(String href) {
        String path = href;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        String id = path.substring(path.lastIndexOf('/') + 1).trim();
        return id.isEmpty() ? null : id;
    }

    private String toAbsoluteUrl(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        return CARS_BASE_URL + (href.startsWith("/") ? href : "/" + href);
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private record TimestampedAdvert(Instant publishedAt, Instant renewedAt) {
    }
}
