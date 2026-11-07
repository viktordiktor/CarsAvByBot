package com.avby.avby.repository;

import com.avby.avby.domain.SeenListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeenListingRepository extends JpaRepository<SeenListing, Long> {

    boolean existsByProfileIdAndAdvertId(Long profileId, String advertId);

    Optional<SeenListing> findByProfileIdAndAdvertId(Long profileId, String advertId);

    /** Global check: this advert has already been claimed for Telegram delivery. */
    boolean existsByAdvertIdAndTelegramClaimedTrue(String advertId);

    boolean existsByAdvertIdAndTelegramSentTrue(String advertId);

    boolean existsByAdvertIdAndTelegramClaimedTrueAndIdNot(String advertId, Long id);

    long countByProfileId(Long profileId);

    long countByFirstSeenAtAfter(Instant after);

    long countByNotifiedFalse();

    List<SeenListing> findTop100ByOrderByFirstSeenAtDesc();

    List<SeenListing> findByProfileIdOrderByFirstSeenAtDesc(Long profileId);

    List<SeenListing> findTop50ByProfileIdAndNotifiedFalseAndTelegramClaimedFalseAndNotifyAttemptsLessThanOrderByFirstSeenAtAsc(
            Long profileId, int maxAttempts);

    void deleteByProfileId(Long profileId);
}
