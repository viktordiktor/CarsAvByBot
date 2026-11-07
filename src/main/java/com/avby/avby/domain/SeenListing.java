package com.avby.avby.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A listing already seen by a profile.
 *
 * <p>The unique key (profile + advert id) prevents duplicate rows for one
 * profile. Cross-profile de-duplication is handled by {@code telegramClaimed}:
 * a listing is claimed globally before the Telegram request is made, so even
 * when it matches several profiles it is sent only once.</p>
 */
@Entity
@Table(
        name = "seen_listings",
        uniqueConstraints = @UniqueConstraint(name = "uk_seen_profile_advert", columnNames = {"profile_id", "advert_id"}),
        indexes = {
                @Index(name = "idx_seen_profile", columnList = "profile_id"),
                @Index(name = "idx_seen_first_seen", columnList = "first_seen_at"),
                @Index(name = "idx_seen_advert_claimed", columnList = "advert_id, telegram_claimed")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SeenListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "advert_id", nullable = false, length = 40)
    private String advertId;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String url;

    @Column(length = 100)
    private String priceText;

    @Column(length = 100)
    private String yearText;

    @Column(length = 300)
    private String paramsText;

    @Column(length = 100)
    private String mileageText;

    @Column(length = 120)
    private String location;

    @Column(length = 120)
    private String publishedText;

    @Column(length = 10000)
    private String description;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 300)
    private String badges;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt = Instant.now();

    /** Processed: no longer waiting in the automatic retry queue. */
    @Column(nullable = false)
    private boolean notified = false;

    /**
     * Confirmed delivery to Telegram. Partial multi-chat delivery also counts
     * as sent: successful chats must never receive the same listing again.
     */
    @Column(name = "telegram_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean telegramSent = false;

    /**
     * This row owns the global delivery attempt for the advert. The flag is set
     * and committed before the network call. It is released only when Telegram
     * definitely did not accept the message; after an ambiguous failure it stays
     * set forever, which guarantees the listing is never sent twice.
     */
    @Column(name = "telegram_claimed", nullable = false, columnDefinition = "boolean default false")
    private boolean telegramClaimed = false;

    @Column(nullable = false)
    private int notifyAttempts = 0;

    @Column(length = 1000)
    private String notifyError;

    public String getFirstSeenFormatted() {
        if (firstSeenAt == null) {
            return "";
        }
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(firstSeenAt);
    }
}
