package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.TrackingToken;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PublicTrack;
import tz.co.chambaka.glyt.web.dto.GlytDtos.TimelineStep;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTrackServiceTest {

    @Mock BagRepository bags;

    private PublicTrackService service;

    @BeforeEach
    void setUp() {
        service = new PublicTrackService(bags, TestFixtures.appProperties());
    }

    private Bag bag(BagStatus status, String token, Instant revokedAt) {
        Bag bag = ServiceTestData.bag(1L, status);
        bag.setUpdatedAt(Instant.parse("2026-01-05T08:30:00Z"));
        if (token != null) {
            TrackingToken tracking = new TrackingToken();
            tracking.setBag(bag);
            tracking.setToken(token);
            tracking.setRevokedAt(revokedAt);
            bag.setTracking(tracking);
        }
        when(bags.findByCodeIgnoreCase("GL-1")).thenReturn(Optional.of(bag));
        return bag;
    }

    private static long done(PublicTrack track) {
        return track.timeline().stream().filter(TimelineStep::done).count();
    }

    @Test
    void trackReturnsTimelineAndPickupDetails() {
        bag(BagStatus.RECEIVED_ZNZ, "tok", null);

        PublicTrack track = service.track("GL-1", "tok");

        assertThat(track.bagCode()).isEqualTo("GL-1");
        assertThat(track.status()).isEqualTo(BagStatus.RECEIVED_ZNZ.customerLabel());
        // 08:30 UTC is 11:30 in Dar es Salaam (UTC+3)
        assertThat(track.lastUpdated()).isEqualTo("05 Jan 2026, 11:30");
        assertThat(track.timeline()).hasSize(7);
        assertThat(done(track)).isEqualTo(4);
        assertThat(track.timeline().get(3).label()).isEqualTo("Received in Zanzibar");
        assertThat(track.timeline().get(3).done()).isTrue();
        assertThat(track.timeline().get(4).done()).isFalse();
        assertThat(track.pickup().name()).isEqualTo("Test desk");
        assertThat(track.pickup().place()).isEqualTo("Test place");
        assertThat(track.pickup().hours()).isEqualTo("06:00–22:00");
        assertThat(track.pickup().phone()).isEqualTo("+255 777 000 111");
        assertThat(track.pickup().whatsapp()).isEqualTo("255777000111");
    }

    @Test
    void timelineProgressesWithStatus() {
        BagStatus[] order = {BagStatus.BOOKED, BagStatus.RECEIVED_KIA, BagStatus.SEALED, BagStatus.HANDED_AIRLINE,
                BagStatus.RECEIVED_ZNZ, BagStatus.STORED, BagStatus.READY, BagStatus.COLLECTED};
        // Each later status completes exactly one more of the seven steps (BOOKED completes none).
        for (int i = 0; i < order.length; i++) {
            bag(order[i], "tok", null);
            assertThat(done(service.track("GL-1", "tok"))).isEqualTo(i);
        }
    }

    @Test
    void cancelledBagShowsNoProgress() {
        bag(BagStatus.CANCELLED, "tok", null);
        PublicTrack track = service.track("GL-1", "tok");
        assertThat(done(track)).isZero();
        assertThat(track.status()).isEqualTo("Booking cancelled");
    }

    @Test
    void trackRejectsUnknownCodeWrongTokenMissingTokenAndRevokedLink() {
        when(bags.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());
        assertNotFound(() -> service.track("NOPE", "tok"));

        bag(BagStatus.READY, "tok", null);
        assertNotFound(() -> service.track("GL-1", "other"));
        assertNotFound(() -> service.track("GL-1", null));

        bag(BagStatus.READY, "tok", Instant.now());
        assertNotFound(() -> service.track("GL-1", "tok"));

        bag(BagStatus.READY, null, null);
        assertNotFound(() -> service.track("GL-1", "tok"));
    }

    private static void assertNotFound(Runnable action) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("tracking link is not valid");
    }
}
