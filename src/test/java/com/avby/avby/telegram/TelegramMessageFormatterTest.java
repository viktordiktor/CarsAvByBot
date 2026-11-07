package com.avby.avby.telegram;

import com.avby.avby.avby.AvbyListing;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void formatsListingAndEscapesHtml() {
        AvbyListing listing = new AvbyListing(
                "142994121",
                "BMW <X5> & Co",
                "https://cars.av.by/bmw/x5/142994121",
                "10 000 $",
                "2015 г.",
                "автомат, 3,0 л, дизель",
                "200 000 км",
                "Минск",
                "сегодня",
                "Срочно & торг <быстро>",
                "https://avcdn.av.by/photo.jpg",
                "Топ"
        );

        String text = formatter.format(listing, 4096);

        assertThat(text).contains("BMW &lt;X5&gt; &amp; Co");
        assertThat(text).contains("10 000 $");
        assertThat(text).contains("2015 г.");
        assertThat(text).contains("200 000 км");
        assertThat(text).contains("Срочно &amp; торг &lt;быстро&gt;");
        assertThat(text).contains("href=\"https://cars.av.by/bmw/x5/142994121\"");
        assertThat(text).contains("Открыть объявление на av.by");
    }

    @Test
    void truncatesLongDescription() {
        String longDescription = "а".repeat(1000);
        AvbyListing listing = new AvbyListing(
                "1", "Машина", "https://cars.av.by/car/1", "1 $", "2020 г.",
                "", "", "", "", longDescription, "", ""
        );

        String text = formatter.format(listing, 1024);

        assertThat(text.length()).isLessThan(1600);
        assertThat(text).contains("…");
    }
}
