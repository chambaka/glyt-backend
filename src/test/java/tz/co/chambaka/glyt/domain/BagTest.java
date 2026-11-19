package tz.co.chambaka.glyt.domain;

import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BagTest {

    @Test
    void defaultsAreSensible() {
        Bag bag = new Bag();

        assertThat(bag.getExceptionState()).isEqualTo(ExceptionState.NONE);
        assertThat(bag.isProhibitedConfirmed()).isFalse();
        assertThat(bag.getCreatedAt()).isNotNull();
        assertThat(bag.getUpdatedAt()).isNotNull();
        assertThat(bag.getId()).isNull();
    }

    @Test
    void getManifestIsNullWithoutLink() {
        assertThat(new Bag().getManifest()).isNull();
    }

    @Test
    void getManifestReturnsLinkedManifest() {
        Bag bag = new Bag();
        Manifest manifest = new Manifest();
        manifest.setNumber("M-001");
        ManifestBag link = new ManifestBag();
        link.setManifest(manifest);
        link.setBag(bag);
        bag.setManifestLink(link);

        assertThat(bag.getManifest()).isSameAs(manifest);
    }

    @Test
    void trackingValueIsNullWithoutToken() {
        assertThat(new Bag().trackingValue()).isNull();
    }

    @Test
    void trackingValueReturnsActiveToken() {
        Bag bag = new Bag();
        TrackingToken token = new TrackingToken();
        token.setToken("TRK-ABC");
        bag.setTracking(token);

        assertThat(bag.trackingValue()).isEqualTo("TRK-ABC");
    }

    @Test
    void trackingValueIsNullWhenRevoked() {
        Bag bag = new Bag();
        TrackingToken token = new TrackingToken();
        token.setToken("TRK-ABC");
        token.setRevokedAt(Instant.now());
        bag.setTracking(token);

        assertThat(bag.trackingValue()).isNull();
    }

    @Test
    void touchRefreshesUpdatedAt() {
        Bag bag = new Bag();
        bag.setUpdatedAt(Instant.EPOCH);

        bag.touch();

        assertThat(bag.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void settersAndGettersWork() {
        Bag bag = new Bag();
        bag.setCode("BAG-1");
        bag.setColor("Red");
        bag.setWeightKg(18.5);
        bag.setDescription("Red suitcase");
        bag.setSealNumber("S-1");
        bag.setCurrentLocation("KIA");
        bag.setProhibitedConfirmed(true);

        assertThat(bag.getCode()).isEqualTo("BAG-1");
        assertThat(bag.getColor()).isEqualTo("Red");
        assertThat(bag.getWeightKg()).isEqualTo(18.5);
        assertThat(bag.getDescription()).isEqualTo("Red suitcase");
        assertThat(bag.getSealNumber()).isEqualTo("S-1");
        assertThat(bag.getCurrentLocation()).isEqualTo("KIA");
        assertThat(bag.isProhibitedConfirmed()).isTrue();
    }
}
