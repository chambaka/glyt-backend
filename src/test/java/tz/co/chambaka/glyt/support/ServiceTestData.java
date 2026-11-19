package tz.co.chambaka.glyt.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.BaggageException;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Small builders and security-context helpers shared by the service unit tests. */
public final class ServiceTestData {

    private ServiceTestData() {}

    public static void authenticate(AuthUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    public static void authenticate(UserAccount account) {
        authenticate(TestFixtures.authUser(account));
    }

    public static void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    public static AppProperties withMail(boolean exposeResetToken) {
        AppProperties base = TestFixtures.appProperties();
        return new AppProperties(base.jwt(), base.cors(), base.storage(), base.ops(), base.seed(),
                new AppProperties.Mail(exposeResetToken), base.payments());
    }

    public static AppProperties withOps(AppProperties.Ops ops) {
        AppProperties base = TestFixtures.appProperties();
        return new AppProperties(base.jwt(), base.cors(), base.storage(), ops, base.seed(), base.mail(), base.payments());
    }

    public static AppProperties withPayments(AppProperties.Payments payments) {
        AppProperties base = TestFixtures.appProperties();
        return new AppProperties(base.jwt(), base.cors(), base.storage(), base.ops(), base.seed(), base.mail(), payments);
    }

    public static Customer customer(long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerNumber("CUS-" + id);
        customer.setFullName("Jane Traveler");
        customer.setPhone("+255712000111");
        customer.setEmail("jane@example.com");
        customer.setNationality("TZ");
        customer.setPassportNumber("AB1234567");
        return customer;
    }

    public static Booking booking(long id, Customer customer) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingNumber("BK-" + id);
        booking.setCustomer(customer);
        booking.setStatus("OPEN");
        booking.setInternationalArrivalFlight("KQ 100");
        booking.setKiaArrivalDate("2026-01-10");
        booking.setZanzibarTravelDate("2026-01-11");
        booking.setZanzibarFlight("PW 200");
        booking.setTourOperator("Safari Co");
        booking.setBookingReference("REF1");
        booking.setZanzibarHotel("Hotel Z");
        booking.setNotes("notes");
        return booking;
    }

    public static Bag bag(long id, BagStatus status) {
        Customer customer = customer(1L);
        Booking booking = booking(1L, customer);
        Bag bag = new Bag();
        bag.setId(id);
        bag.setCode("GL-" + id);
        bag.setCustomer(customer);
        bag.setBooking(booking);
        bag.setStatus(status);
        bag.setExceptionState(ExceptionState.NONE);
        bag.setBagType(BagType.SUITCASE);
        bag.setColor("Black");
        bag.setWeightKg(15.0);
        bag.setDescription("Black suitcase");
        bag.setCurrentLocation("KIA");
        return bag;
    }

    public static BaggageException exception(long id, Bag bag, ExceptionType type, Priority priority, ExceptionStatus status) {
        BaggageException row = new BaggageException();
        row.setId(id);
        row.setBag(bag);
        row.setType(type);
        row.setPriority(priority);
        row.setStatus(status);
        row.setDescription("desc " + id);
        row.setLocation(bag.getCurrentLocation());
        row.setRaisedBy(TestFixtures.staff(tz.co.chambaka.glyt.domain.enums.Role.KIA_OFFICER, "KIA"));
        return row;
    }

    /**
     * Mockito answer for {@code repo.save(entity)} that mimics JPA identity generation by calling
     * {@code setId(Long)} reflectively when the entity has no id yet, then returns the entity.
     */
    public static <T> org.mockito.stubbing.Answer<T> assignId(long id) {
        return invocation -> {
            T entity = invocation.getArgument(0);
            try {
                var getter = entity.getClass().getMethod("getId");
                if (getter.invoke(entity) == null) {
                    entity.getClass().getMethod("setId", Long.class).invoke(entity, id);
                }
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
            return entity;
        };
    }

    public static ApiException expectApi(Runnable action) {
        ApiException ex = catchThrowableOfType(ApiException.class, action::run);
        assertThat(ex).as("expected ApiException").isNotNull();
        return ex;
    }
}
