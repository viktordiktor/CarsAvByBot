package com.avby.avby.avby;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvbyParserTest {

    private final AvbyParser parser = new AvbyParser();

    private String fixture() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/avby-page.html")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesListingCards() throws IOException {
        List<AvbyListing> listings = parser.parse(fixture());

        assertThat(listings).hasSize(2);

        AvbyListing first = listings.get(0);
        assertThat(first.advertId()).isEqualTo("142994121");
        assertThat(first.url()).isEqualTo("https://cars.av.by/volkswagen/multivan/142994121");
        assertThat(first.title()).isEqualTo("Volkswagen Multivan T6, 8 мест");
        assertThat(first.priceText()).isEqualTo("87 651 руб.");
        assertThat(first.yearText()).isEqualTo("2016 г.");
        assertThat(first.paramsText()).isEqualTo("робот, 2,0 л, дизель, микроавтобус пассажирский");
        assertThat(first.mileageText()).isEqualTo("342 000 км");
        assertThat(first.location()).isEqualTo("Минск");
        assertThat(first.publishedText()).isEqualTo("28 октября");
        assertThat(first.description()).isEqualTo("Продаю срочно, торг. Полностью на ходу.");
        assertThat(first.imageUrl()).isEqualTo("https://avcdn.av.by/advertpreview/0016/4933/2542.jpg");
        assertThat(first.badges()).isEqualTo("Топ");

        AvbyListing second = listings.get(1);
        assertThat(second.advertId()).isEqualTo("555");
        assertThat(second.url()).isEqualTo("https://cars.av.by/audi/a6/555");
        assertThat(second.priceText()).isEqualTo("18 500 $");
    }

    @Test
    void parsesExactTimestampsFromEmbeddedJson() throws IOException {
        String nextData = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"pageProps\":{\"filter\":{\"main\":{\"adverts\":["
                + "{\"id\":142994121,\"publishedAt\":\"2026-11-10T09:30:30+0000\"},"
                + "{\"id\":555,\"publishedAt\":\"2025-09-03T09:30:30+0000\","
                + "\"renewedAt\":\"2026-11-10T09:38:20+0000\"}"
                + "]}}}}}</script>";
        String html = fixture().replace("</head>", nextData + "</head>");

        List<AvbyListing> listings = parser.parse(html);

        assertThat(listings.get(0).publishedAt()).isEqualTo(Instant.parse("2026-11-10T09:30:30Z"));
        assertThat(listings.get(0).renewedAt()).isNull();
        assertThat(listings.get(1).publishedAt()).isEqualTo(Instant.parse("2025-09-03T09:30:30Z"));
        assertThat(listings.get(1).renewedAt()).isEqualTo(Instant.parse("2026-11-10T09:38:20Z"));
    }

    @Test
    void ignoresCardsWithoutLink() throws IOException {
        List<AvbyListing> listings = parser.parse(fixture());

        assertThat(listings)
                .extracting(AvbyListing::title)
                .doesNotContain("Карточка без ссылки");
    }

    @Test
    void shortDescriptionIsSingleLineAndTruncated() {
        AvbyListing listing = new AvbyListing(
                "1", "t", "u", "", "", "", "", "", "",
                "Первая строка\n\nвторая строка", "", ""
        );

        assertThat(listing.shortDescription(12)).isEqualTo("Первая стро…");
        assertThat(listing.shortDescription(500)).isEqualTo("Первая строка вторая строка");
    }
}
