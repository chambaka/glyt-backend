package tz.co.chambaka.glyt.domain;

import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.support.TestFixtures;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers small pieces of logic and defaults on JPA entities (no persistence context needed). */
class EntityLogicTest {

    // ---- UserAccount ----

    @Test
    void userLocationCodeIsAllWithoutLocation() {
        UserAccount user = new UserAccount();

        assertThat(user.locationCode()).isEqualTo("ALL");
    }

    @Test
    void userLocationCodeUsesLocation() {
        UserAccount user = TestFixtures.staff(Role.KIA_OFFICER, "KIA");

        assertThat(user.locationCode()).isEqualTo("KIA");
    }

    @Test
    void userDefaults() {
        UserAccount user = new UserAccount();

        assertThat(user.isActive()).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getTokenVersion()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void userTouchRefreshesUpdatedAt() {
        UserAccount user = new UserAccount();
        user.setUpdatedAt(Instant.EPOCH);

        user.touch();

        assertThat(user.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    // ---- @PreUpdate hooks ----

    @Test
    void airlineTouchAndDefaults() {
        Airline airline = new Airline();
        assertThat(airline.getCountry()).isEqualTo("TZ");
        assertThat(airline.isActive()).isTrue();
        assertThat(airline.getSortOrder()).isEqualTo(100);

        airline.setUpdatedAt(Instant.EPOCH);
        airline.touch();

        assertThat(airline.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void locationTouch() {
        Location location = new Location();
        location.setUpdatedAt(Instant.EPOCH);

        location.touch();

        assertThat(location.getUpdatedAt()).isAfter(Instant.EPOCH);
        assertThat(location.isActive()).isTrue();
    }

    @Test
    void bookingTouch() {
        Booking booking = new Booking();
        booking.setUpdatedAt(Instant.EPOCH);

        booking.touch();

        assertThat(booking.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void customerTouch() {
        Customer customer = new Customer();
        customer.setUpdatedAt(Instant.EPOCH);

        customer.touch();

        assertThat(customer.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void supportTicketTouch() {
        SupportTicket ticket = new SupportTicket();
        ticket.setUpdatedAt(Instant.EPOCH);

        ticket.touch();

        assertThat(ticket.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void customerDeviceTokenTouch() {
        CustomerDeviceToken token = new CustomerDeviceToken();
        token.setUpdatedAt(Instant.EPOCH);

        token.touch();

        assertThat(token.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void paymentTransactionTouchAndDefaults() {
        PaymentTransaction tx = new PaymentTransaction();
        assertThat(tx.getCurrency()).isEqualTo("TZS");

        tx.setUpdatedAt(Instant.EPOCH);
        tx.touch();

        assertThat(tx.getUpdatedAt()).isAfter(Instant.EPOCH);
    }

    // ---- simple defaults ----

    @Test
    void counterHoldsValue() {
        Counter counter = new Counter();
        counter.setName("bag");
        counter.setValue(5);

        assertThat(counter.getName()).isEqualTo("bag");
        assertThat(counter.getValue()).isEqualTo(5);
    }

    @Test
    void servicePriceDefaults() {
        ServicePrice price = new ServicePrice();

        assertThat(price.getCurrency()).isEqualTo("TZS");
        assertThat(price.getUnit()).isEqualTo("PER_BAG");
        assertThat(price.isActive()).isTrue();
    }

    @Test
    void serviceCatalogItemDefaults() {
        assertThat(new ServiceCatalogItem().isActive()).isTrue();
    }

    @Test
    void storageLocationDefaults() {
        StorageLocation location = new StorageLocation();

        assertThat(location.isActive()).isTrue();
        assertThat(location.getFacilityCode()).isEqualTo("ZNZ");
    }

    @Test
    void manifestDefaults() {
        Manifest manifest = new Manifest();

        assertThat(manifest.getRoute()).isEqualTo("JRO-ZNZ");
        assertThat(manifest.getStatus()).isEqualTo(tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus.DRAFT);
    }

    @Test
    void customerAccountDefaults() {
        CustomerAccount account = new CustomerAccount();

        assertThat(account.isActive()).isTrue();
        assertThat(account.getPreferredLanguage()).isEqualTo("en");
        assertThat(account.getTokenVersion()).isZero();
    }
}
