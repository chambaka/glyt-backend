package tz.co.chambaka.glyt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.CustomerDeviceToken;
import tz.co.chambaka.glyt.domain.Quotation;
import tz.co.chambaka.glyt.domain.ServiceCatalogItem;
import tz.co.chambaka.glyt.domain.ServicePrice;
import tz.co.chambaka.glyt.domain.SupportTicket;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.CustomerDeviceTokenRepository;
import tz.co.chambaka.glyt.repo.QuotationRepository;
import tz.co.chambaka.glyt.repo.ServiceCatalogRepository;
import tz.co.chambaka.glyt.repo.ServicePriceRepository;
import tz.co.chambaka.glyt.repo.SupportTicketRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerBookingRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuoteRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuotationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerPortalServiceTest {

    @Mock CustomerAuthService auth;
    @Mock ServiceCatalogRepository services;
    @Mock ServicePriceRepository prices;
    @Mock QuotationRepository quotations;
    @Mock BookingRepository bookings;
    @Mock BagRepository bags;
    @Mock SupportTicketRepository tickets;
    @Mock CustomerDeviceTokenRepository devices;
    @Mock CodeGenerator codes;
    @Mock NotificationService notifications;
    @Mock CustomerBookingService bookingViews;

    private CustomerPortalService service;
    private CustomerAccount account;
    private Customer customer;
    private ServiceCatalogItem item;
    private ServicePrice price;

    @BeforeEach
    void setUp() {
        service = new CustomerPortalService(auth, services, prices, quotations, bookings, bags, tickets, devices, codes,
                notifications, bookingViews, new ObjectMapper());
        customer = ServiceTestData.customer(1L);
        account = new CustomerAccount();
        account.setId(7L);
        account.setCustomer(customer);
        account.setEmail("jane@example.com");
        when(auth.requireAccount()).thenReturn(account);

        item = new ServiceCatalogItem();
        item.setId(2L);
        item.setCode("STD");
        item.setName("Standard");
        price = new ServicePrice();
        price.setId(3L);
        price.setAmountMinor(10_000);
        price.setCurrency("TZS");
        price.setUnit("PER_BAG");
        when(services.findById(2L)).thenReturn(Optional.of(item));
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(Optional.of(price));

        when(quotations.save(any(Quotation.class))).thenAnswer(ServiceTestData.assignId(4L));
        when(bookings.save(any(Booking.class))).thenAnswer(ServiceTestData.assignId(5L));
        when(tickets.save(any(SupportTicket.class))).thenAnswer(ServiceTestData.assignId(6L));
        when(devices.save(any(CustomerDeviceToken.class))).thenAnswer(ServiceTestData.assignId(8L));
        when(codes.next("booking", "BK-", 6)).thenReturn("BK-000009");
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private Quotation quotation(CustomerAccount owner, Instant expires) {
        Quotation quotation = new Quotation();
        quotation.setId(4L);
        quotation.setService(item);
        quotation.setCustomerAccount(owner);
        quotation.setExpiresAt(expires);
        quotation.setAmountMinor(20_000);
        quotation.setBagCount(2);
        return quotation;
    }

    // ---- createQuote ----

    @Test
    void createQuoteForAuthenticatedCustomer() {
        QuotationView view = service.createQuote(new QuoteRequest(2L, 3), true);

        ArgumentCaptor<Quotation> saved = ArgumentCaptor.forClass(Quotation.class);
        verify(quotations).save(saved.capture());
        assertThat(saved.getValue().getCustomerAccount()).isSameAs(account);
        assertThat(saved.getValue().getAmountMinor()).isEqualTo(30_000);
        assertThat(saved.getValue().getPayloadJson()).contains("\"serviceCode\":\"STD\"");
        assertThat(view.totalAmountMinor()).isEqualTo(30_000);
        assertThat(view.bagCount()).isEqualTo(3);
        assertThat(view.serviceCode()).isEqualTo("STD");
        assertThat(view.expiresAt()).isNotNull();
    }

    @Test
    void createQuoteAnonymousDoesNotLookUpAccount() {
        service.createQuote(new QuoteRequest(2L, 1), false);
        verify(auth, never()).requireAccount();
        ArgumentCaptor<Quotation> saved = ArgumentCaptor.forClass(Quotation.class);
        verify(quotations).save(saved.capture());
        assertThat(saved.getValue().getCustomerAccount()).isNull();
    }

    @Test
    void createQuoteToleratesPayloadSerialisationFailure() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new IllegalStateException("boom"));
        CustomerPortalService brokenService = new CustomerPortalService(auth, services, prices, quotations, bookings, bags,
                tickets, devices, codes, notifications, bookingViews, broken);
        brokenService.createQuote(new QuoteRequest(2L, 1), false);
        ArgumentCaptor<Quotation> saved = ArgumentCaptor.forClass(Quotation.class);
        verify(quotations).save(saved.capture());
        assertThat(saved.getValue().getPayloadJson()).isNull();
    }

    @Test
    void createQuoteValidatesInput() {
        api(() -> service.createQuote(new QuoteRequest(null, 1), false), HttpStatus.BAD_REQUEST, "serviceId and bagCount");
        api(() -> service.createQuote(new QuoteRequest(2L, null), false), HttpStatus.BAD_REQUEST, "serviceId and bagCount");
        api(() -> service.createQuote(new QuoteRequest(2L, 0), false), HttpStatus.BAD_REQUEST, "serviceId and bagCount");
        when(services.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.createQuote(new QuoteRequest(99L, 1), false), HttpStatus.NOT_FOUND, "Service was not found");
        when(prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(2L)).thenReturn(Optional.empty());
        api(() -> service.createQuote(new QuoteRequest(2L, 1), false), HttpStatus.BAD_REQUEST, "No active price");
    }

    // ---- getQuote ----

    @Test
    void getQuoteReturnsOwnQuoteAndAnonymousQuote() {
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(account, null)));
        QuotationView own = service.getQuote(4L);
        assertThat(own.expiresAt()).isNull();

        when(quotations.findById(5L)).thenReturn(Optional.of(quotation(null, Instant.now().plusSeconds(60))));
        assertThat(service.getQuote(5L).expiresAt()).isNotNull();
    }

    @Test
    void getQuoteRejectsOtherAccountsAndGuests() {
        CustomerAccount other = new CustomerAccount();
        other.setId(99L);
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(other, null)));
        api(() -> service.getQuote(4L), HttpStatus.FORBIDDEN, "cannot view");

        when(auth.requireAccount()).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Sign in"));
        api(() -> service.getQuote(4L), HttpStatus.FORBIDDEN, "cannot view");
    }

    @Test
    void getQuoteNotFound() {
        when(quotations.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.getQuote(404L), HttpStatus.NOT_FOUND, "Quotation was not found");
    }

    // ---- createBooking ----

    private CustomerBookingRequest bookingRequest(Long quotationId) {
        return new CustomerBookingRequest(quotationId, " KQ 1 ", "2026-03-01", "2026-03-02", " PW 1 ", null, " ", " Hotel ", " note ");
    }

    @Test
    void createBookingCreatesPendingPaymentBooking() {
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(account, Instant.now().plusSeconds(60))));
        BookingView expected = org.mockito.Mockito.mock(BookingView.class);
        when(bookingViews.getBooking(5L)).thenReturn(expected);

        BookingView view = service.createBooking(bookingRequest(4L));

        assertThat(view).isSameAs(expected);
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(saved.getValue().getBookingNumber()).isEqualTo("BK-000009");
        assertThat(saved.getValue().getBookingReference()).isNull();
        assertThat(saved.getValue().getZanzibarHotel()).isEqualTo("Hotel");
        verify(notifications).enqueue(eq("EMAIL"), eq("jane@example.com"), eq("BOOKING_CREATED"), eq("{\"bookingNumber\":\"BK-000009\"}"));
    }

    @Test
    void createBookingAcceptsAnonymousQuoteWithoutExpiry() {
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(null, null)));
        service.createBooking(bookingRequest(4L));
        verify(bookings).save(any(Booking.class));
    }

    @Test
    void createBookingRejectsInvalidStates() {
        account.setCustomer(null);
        api(() -> service.createBooking(bookingRequest(4L)), HttpStatus.BAD_REQUEST, "profile is incomplete");
        account.setCustomer(customer);

        api(() -> service.createBooking(bookingRequest(null)), HttpStatus.BAD_REQUEST, "quotation is required");

        when(quotations.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.createBooking(bookingRequest(404L)), HttpStatus.NOT_FOUND, "Quotation was not found");

        CustomerAccount other = new CustomerAccount();
        other.setId(99L);
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(other, null)));
        api(() -> service.createBooking(bookingRequest(4L)), HttpStatus.FORBIDDEN, "another account");

        when(quotations.findById(5L)).thenReturn(Optional.of(quotation(account, Instant.now().minusSeconds(5))));
        api(() -> service.createBooking(bookingRequest(5L)), HttpStatus.BAD_REQUEST, "expired");
        verify(bookings, never()).save(any());
    }

    @Test
    void createBookingValidatesTravelFields() {
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation(account, null)));
        api(() -> service.createBooking(new CustomerBookingRequest(4L, null, "2026-03-01", "2026-03-02", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "International arrival flight");
        api(() -> service.createBooking(new CustomerBookingRequest(4L, "KQ", "x", "2026-03-02", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "KIA arrival date");
        api(() -> service.createBooking(new CustomerBookingRequest(4L, "KQ", "2026-03-01", "x", null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "Zanzibar travel date");
    }

    // ---- myBookings / cancel ----

    @Test
    void myBookingsDelegatesToBookingViews() {
        when(bookingViews.listBookings(1L)).thenReturn(List.of());
        assertThat(service.myBookings()).isEmpty();
        verify(bookingViews).listBookings(1L);
    }

    @Test
    void myBookingsIsEmptyWithoutCustomerProfile() {
        account.setCustomer(null);
        assertThat(service.myBookings()).isEmpty();
        verify(bookingViews, never()).listBookings(any());
    }

    private Booking booking(String status) {
        Booking booking = ServiceTestData.booking(5L, customer);
        booking.setStatus(status);
        when(bookings.findById(5L)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    void cancelBookingCancelsBookingAndItsBags() {
        Booking booking = booking("OPEN");
        Bag first = ServiceTestData.bag(1L, BagStatus.BOOKED);
        Bag second = ServiceTestData.bag(2L, BagStatus.BOOKED);
        when(bags.findByBookingId(5L)).thenReturn(List.of(first, second));

        service.cancelBooking(5L);

        assertThat(booking.getStatus()).isEqualTo("CANCELLED");
        assertThat(first.getStatus()).isEqualTo(BagStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(BagStatus.CANCELLED);
        verify(notifications).enqueue(eq("EMAIL"), eq("jane@example.com"), eq("BOOKING_CANCELLED"), anyString());
        verify(bookingViews).getBooking(5L);
    }

    @Test
    void cancelPendingPaymentBookingWithoutBags() {
        Booking booking = booking("PENDING_PAYMENT");
        when(bags.findByBookingId(5L)).thenReturn(List.of());
        service.cancelBooking(5L);
        assertThat(booking.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelBookingRejectsProcessedBagsAndClosedBookings() {
        booking("OPEN");
        when(bags.findByBookingId(5L)).thenReturn(List.of(ServiceTestData.bag(1L, BagStatus.RECEIVED_KIA)));
        api(() -> service.cancelBooking(5L), HttpStatus.BAD_REQUEST, "unprocessed bags");

        booking("CLOSED");
        when(bags.findByBookingId(5L)).thenReturn(List.of());
        api(() -> service.cancelBooking(5L), HttpStatus.BAD_REQUEST, "unprocessed bags");
    }

    @Test
    void cancelBookingRejectsForeignBookingMissingBookingAndMissingProfile() {
        when(bookings.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.cancelBooking(404L), HttpStatus.NOT_FOUND, "Booking was not found");

        Booking booking = booking("OPEN");
        booking.setCustomer(ServiceTestData.customer(2L));
        api(() -> service.cancelBooking(5L), HttpStatus.FORBIDDEN, "cannot cancel");

        account.setCustomer(null);
        api(() -> service.cancelBooking(5L), HttpStatus.FORBIDDEN, "cannot cancel");
    }

    // ---- tickets ----

    @Test
    void createTicketWithoutBag() {
        SupportTicketView view = service.createTicket(new SupportTicketRequest(" Help ", " Please ", null));
        assertThat(view.subject()).isEqualTo("Help");
        assertThat(view.bagId()).isNull();
        assertThat(view.id()).isEqualTo(6L);
        verify(notifications).enqueue("EMAIL", "jane@example.com", "SUPPORT_TICKET_CREATED", "{\"ticketId\":6}");
    }

    @Test
    void createTicketForOwnBag() {
        Bag bag = ServiceTestData.bag(9L, BagStatus.STORED);
        when(bags.findById(9L)).thenReturn(Optional.of(bag));
        assertThat(service.createTicket(new SupportTicketRequest("s", "b", 9L)).bagId()).isEqualTo(9L);
    }

    @Test
    void createTicketRejectsInvalidInput() {
        api(() -> service.createTicket(new SupportTicketRequest(null, "b", null)), HttpStatus.BAD_REQUEST, "Subject");
        api(() -> service.createTicket(new SupportTicketRequest("s", " ", null)), HttpStatus.BAD_REQUEST, "Message");

        when(bags.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.createTicket(new SupportTicketRequest("s", "b", 404L)), HttpStatus.NOT_FOUND, "Bag was not found");

        Bag foreign = ServiceTestData.bag(9L, BagStatus.STORED);
        foreign.setCustomer(ServiceTestData.customer(2L));
        when(bags.findById(9L)).thenReturn(Optional.of(foreign));
        api(() -> service.createTicket(new SupportTicketRequest("s", "b", 9L)), HttpStatus.FORBIDDEN, "cannot open a ticket");

        account.setCustomer(null);
        api(() -> service.createTicket(new SupportTicketRequest("s", "b", 9L)), HttpStatus.FORBIDDEN, "cannot open a ticket");
    }

    @Test
    void myTicketsListsAccountTickets() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(1L);
        ticket.setSubject("s");
        ticket.setBody("b");
        ticket.setBag(ServiceTestData.bag(3L, BagStatus.READY));
        when(tickets.findByCustomerAccountIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(ticket));
        List<SupportTicketView> views = service.myTickets();
        assertThat(views).hasSize(1);
        assertThat(views.get(0).bagId()).isEqualTo(3L);
        assertThat(views.get(0).status()).isEqualTo("OPEN");
    }

    // ---- devices ----

    @Test
    void registerDeviceCreatesNewRow() {
        when(devices.findByDeviceToken("tok")).thenReturn(Optional.empty());

        DeviceRegisterResponse response = service.registerDevice(new DeviceRegisterRequest(" tok ", " android "));

        assertThat(response.platform()).isEqualTo("ANDROID");
        assertThat(response.active()).isTrue();
        assertThat(response.id()).isEqualTo(8L);
        ArgumentCaptor<CustomerDeviceToken> saved = ArgumentCaptor.forClass(CustomerDeviceToken.class);
        verify(devices).save(saved.capture());
        assertThat(saved.getValue().getCustomerAccount()).isSameAs(account);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void registerDeviceReactivatesExistingRowAndBackfillsCreatedAt() {
        CustomerDeviceToken existing = new CustomerDeviceToken();
        existing.setId(50L);
        existing.setActive(false);
        existing.setCreatedAt(null);
        when(devices.findByDeviceToken("tok")).thenReturn(Optional.of(existing));

        DeviceRegisterResponse response = service.registerDevice(new DeviceRegisterRequest("tok", "ios"));

        assertThat(response.id()).isEqualTo(50L);
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getCreatedAt()).isNotNull();
    }

    @Test
    void registerDeviceKeepsExistingCreatedAt() {
        CustomerDeviceToken existing = new CustomerDeviceToken();
        existing.setId(50L);
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        existing.setCreatedAt(created);
        when(devices.findByDeviceToken("tok")).thenReturn(Optional.of(existing));
        service.registerDevice(new DeviceRegisterRequest("tok", "web"));
        service.registerDevice(new DeviceRegisterRequest("tok", "local"));
        assertThat(existing.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void registerDeviceValidatesInput() {
        api(() -> service.registerDevice(new DeviceRegisterRequest(null, "ios")), HttpStatus.BAD_REQUEST, "Device token");
        api(() -> service.registerDevice(new DeviceRegisterRequest("t", null)), HttpStatus.BAD_REQUEST, "Platform is required");
        api(() -> service.registerDevice(new DeviceRegisterRequest("t", "symbian")), HttpStatus.BAD_REQUEST, "Platform must be");
    }

    @Test
    void deactivateDeviceMarksOwnDeviceInactive() {
        CustomerDeviceToken row = new CustomerDeviceToken();
        row.setCustomerAccount(account);
        row.setActive(true);
        when(devices.findByDeviceToken("tok")).thenReturn(Optional.of(row));
        service.deactivateDevice(" tok ");
        assertThat(row.isActive()).isFalse();
    }

    @Test
    void deactivateDeviceRejectsOthersAndIgnoresUnknown() {
        CustomerAccount other = new CustomerAccount();
        other.setId(99L);
        CustomerDeviceToken foreign = new CustomerDeviceToken();
        foreign.setCustomerAccount(other);
        foreign.setActive(true);
        when(devices.findByDeviceToken("foreign")).thenReturn(Optional.of(foreign));
        api(() -> service.deactivateDevice("foreign"), HttpStatus.FORBIDDEN, "cannot update that device");
        assertThat(foreign.isActive()).isTrue();

        when(devices.findByDeviceToken("none")).thenReturn(Optional.empty());
        service.deactivateDevice("none");
        api(() -> service.deactivateDevice(" "), HttpStatus.BAD_REQUEST, "Device token is required");
    }
}
