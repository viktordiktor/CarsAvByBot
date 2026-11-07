package com.avby.avby.avby;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    private static final String CARS_BASE_URL = "https://cars.av.by";

    public List<AvbyListing> parse(String html) {
        Document document = Jsoup.parse(html);
        Elements cards = document.select("div.listing-item");
        Map<String, AvbyListing> byId = new LinkedHashMap<>();

        for (Element card : cards) {
            AvbyListing listing = parseCard(card);
            if (listing != null) {
                byId.putIfAbsent(listing.advertId(), listing);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private AvbyListing parseCard(Element card) {
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
                String.join(" · ", badgeLabels)
        );
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
}
