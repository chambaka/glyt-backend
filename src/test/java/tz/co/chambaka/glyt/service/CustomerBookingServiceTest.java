package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.CustomerRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRecord;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerBookingServiceTest {

    @Mock CustomerRepository customers;
    @Mock BookingRepository bookings;
    @Mock CodeGenerator codes;
    @Mock StaffContext staff;

    private CustomerBookingService service;

    @BeforeEach
    void setUp() {
        service = new CustomerBookingService(customers, bookings, codes, staff);
        when(customers.save(any(Customer.class))).thenAnswer(ServiceTestData.assignId(9L));
        when(customers.findByPassportNumberIgnoreCaseAndNationalityIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(bookings.save(any(Booking.class))).thenAnswer(ServiceTestData.assignId(3L));
        when(codes.next("customer", "CU-", 6)).thenReturn("CU-000009");
        when(codes.next("booking", "BK-", 6)).thenReturn("BK-000003");
    }

    private static CustomerInput customerInput() {
        return new CustomerInput(" Jane ", " 0712 ", " j@x.com ", " TZ ", " P123 ");
    }

    private static BookingInput bookingInput(Long customerId) {
        return new BookingInput(customerId, " KQ 1 ", "2026-03-01", "2026-03-02", " PW 1 ", "  ", null, " Hotel ", " n ");
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    // ---- createCustomer ----

    @Test
    void createCustomerPersistsTrimmedValues() {
        CustomerRecord record = service.createCustomer(customerInput());

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(saved.getValue().getCustomerNumber()).isEqualTo("CU-000009");
        assertThat(record.fullName()).isEqualTo("Jane");
        assertThat(record.phone()).isEqualTo("0712");
        assertThat(record.nationality()).isEqualTo("TZ");
        assertThat(record.passportNumber()).isEqualTo("P123");
        verify(staff).require(Role.ADMIN, Role.KIA_OFFICER);
        verify(customers).findByPassportNumberIgnoreCaseAndNationalityIgnoreCase("P123", "TZ");
    }

    @Test
    void createCustomerUpdatesReturningTraveller() {
        Customer existing = ServiceTestData.customer(4L);
        when(customers.findByPassportNumberIgnoreCaseAndNationalityIgnoreCase("P123", "TZ"))
                .thenReturn(Optional.of(existing));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerRecord record = service.createCustomer(new CustomerInput(" Jane Doe ", " 0712999 ", " jane@x.com ", " tz ", " p123 "));

        assertThat(record.id()).isEqualTo(4L);
        assertThat(record.customerNumber()).isEqualTo("CUS-4");
        assertThat(record.fullName()).isEqualTo("Jane Doe");
        assertThat(record.phone()).isEqualTo("0712999");
        assertThat(record.email()).isEqualTo("jane@x.com");
        verify(codes, org.mockito.Mockito.never()).next("customer", "CU-", 6);
    }

    @Test
    void createCustomerValidatesEachField() {
        api(() -> service.createCustomer(new CustomerInput(null, "1", "e", "n", "p")), HttpStatus.BAD_REQUEST, "Full name");
        api(() -> service.createCustomer(new CustomerInput("a", null, "e", "n", "p")), HttpStatus.BAD_REQUEST, "Mobile number");
        api(() -> service.createCustomer(new CustomerInput("a", "1", null, "n", "p")), HttpStatus.BAD_REQUEST, "Email");
        api(() -> service.createCustomer(new CustomerInput("a", "1", "e", "", "p")), HttpStatus.BAD_REQUEST, "Nationality");
        api(() -> service.createCustomer(new CustomerInput("a", "1", "e", "n", " ")), HttpStatus.BAD_REQUEST, "Passport number");
    }

    @Test
    void createCustomerPropagatesPermissionFailure() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN, Role.KIA_OFFICER);
        api(() -> service.createCustomer(customerInput()), HttpStatus.FORBIDDEN, "no");
        verify(customers, org.mockito.Mockito.never()).save(any());
    }

    // ---- getCustomer / search ----

    @Test
    void getCustomerReturnsRecord() {
        when(customers.findById(1L)).thenReturn(Optional.of(ServiceTestData.customer(1L)));
        assertThat(service.getCustomer(1L).customerNumber()).isEqualTo("CUS-1");
        verify(staff).requireStaff();
    }

    @Test
    void getCustomerNotFound() {
        when(customers.findById(2L)).thenReturn(Optional.empty());
        api(() -> service.getCustomer(2L), HttpStatus.NOT_FOUND, "Customer was not found");
    }

    @Test
    void searchWithBlankTermReturnsFirstTwentyCustomers() {
        List<Customer> many = LongStream.rangeClosed(1, 25).mapToObj(ServiceTestData::customer).toList();
        when(customers.findAll()).thenReturn(many);
        assertThat(service.searchCustomers(null)).hasSize(20);
        assertThat(service.searchCustomers("   ")).hasSize(20);
    }

    @Test
    void searchWithTermUsesLikePattern() {
        when(customers.search("%jane%", PageRequest.of(0, 30))).thenReturn(List.of(ServiceTestData.customer(1L)));
        assertThat(service.searchCustomers("  JANE ")).hasSize(1);
    }

    // ---- bookings ----

    @Test
    void createBookingStoresOptionalFieldsAsNullWhenBlank() {
        Customer customer = ServiceTestData.customer(4L);
        when(customers.findById(4L)).thenReturn(Optional.of(customer));

        BookingView view = service.createBooking(bookingInput(4L));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        Booking booking = saved.getValue();
        assertThat(booking.getBookingNumber()).isEqualTo("BK-000003");
        assertThat(booking.getStatus()).isEqualTo("OPEN");
        assertThat(booking.getTourOperator()).isNull();
        assertThat(booking.getBookingReference()).isNull();
        assertThat(booking.getZanzibarFlight()).isEqualTo("PW 1");
        assertThat(view.customerId()).isEqualTo(4L);
        assertThat(view.internationalArrivalFlight()).isEqualTo("KQ 1");
        assertThat(view.zanzibarHotel()).isEqualTo("Hotel");
    }

    @Test
    void createBookingRequiresCustomerId() {
        api(() -> service.createBooking(bookingInput(null)), HttpStatus.BAD_REQUEST, "Customer is required");
    }

    @Test
    void createBookingCustomerNotFound() {
        when(customers.findById(4L)).thenReturn(Optional.empty());
        api(() -> service.createBooking(bookingInput(4L)), HttpStatus.NOT_FOUND, "Customer was not found");
    }

    @Test
    void createBookingValidatesFlightAndDates() {
        when(customers.findById(4L)).thenReturn(Optional.of(ServiceTestData.customer(4L)));
        api(() -> service.createBooking(new BookingInput(4L, null, "2026-03-01", "2026-03-02", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "International arrival flight");
        api(() -> service.createBooking(new BookingInput(4L, "KQ", "bad", "2026-03-02", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "KIA arrival date");
        api(() -> service.createBooking(new BookingInput(4L, "KQ", "2026-03-01", "bad", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "Zanzibar travel date");
    }

    @Test
    void getBookingFindsOrThrows() {
        Booking booking = ServiceTestData.booking(5L, ServiceTestData.customer(1L));
        when(bookings.findById(5L)).thenReturn(Optional.of(booking));
        when(bookings.findById(6L)).thenReturn(Optional.empty());
        assertThat(service.getBooking(5L).bookingNumber()).isEqualTo("BK-5");
        api(() -> service.getBooking(6L), HttpStatus.NOT_FOUND, "Booking was not found");
    }

    @Test
    void listBookingsFiltersByCustomerWhenProvided() {
        Booking booking = ServiceTestData.booking(5L, ServiceTestData.customer(1L));
        when(bookings.findAll()).thenReturn(List.of(booking, booking));
        when(bookings.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(booking));
        assertThat(service.listBookings(null)).hasSize(2);
        assertThat(service.listBookings(1L)).hasSize(1);
    }
}
