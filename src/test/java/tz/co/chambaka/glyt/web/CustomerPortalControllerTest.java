package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.CustomerPortalService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerBookingRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuoteRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuotationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketView;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPortalControllerTest {

    @Mock
    CustomerPortalService portal;
    @Mock
    BookingView booking;
    @Mock
    QuotationView quotation;
    @InjectMocks
    CustomerPortalController controller;

    @Test
    void quoteUsesAuthenticatedFlag() {
        QuoteRequest request = mock(QuoteRequest.class);
        when(portal.createQuote(request, true)).thenReturn(quotation);
        assertThat(controller.quote(request)).isSameAs(quotation);
    }

    @Test
    void getQuoteDelegates() {
        when(portal.getQuote(2L)).thenReturn(quotation);
        assertThat(controller.getQuote(2L)).isSameAs(quotation);
    }

    @Test
    void getQuotePropagatesNotFound() {
        when(portal.getQuote(2L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "missing"));
        assertThatThrownBy(() -> controller.getQuote(2L)).isInstanceOf(ApiException.class);
    }

    @Test
    void bookingsListsMine() {
        List<BookingView> rows = List.of(booking);
        when(portal.myBookings()).thenReturn(rows);
        assertThat(controller.bookings()).isSameAs(rows);
    }

    @Test
    void createBookingDelegates() {
        CustomerBookingRequest request = mock(CustomerBookingRequest.class);
        when(portal.createBooking(request)).thenReturn(booking);
        assertThat(controller.createBooking(request)).isSameAs(booking);
    }

    @Test
    void cancelBookingDelegates() {
        when(portal.cancelBooking(5L)).thenReturn(booking);
        assertThat(controller.cancelBooking(5L)).isSameAs(booking);
    }

    @Test
    void ticketsListsMine() {
        List<SupportTicketView> rows = List.of(mock(SupportTicketView.class));
        when(portal.myTickets()).thenReturn(rows);
        assertThat(controller.tickets()).isSameAs(rows);
    }

    @Test
    void createTicketDelegates() {
        SupportTicketRequest request = mock(SupportTicketRequest.class);
        SupportTicketView view = mock(SupportTicketView.class);
        when(portal.createTicket(request)).thenReturn(view);
        assertThat(controller.createTicket(request)).isSameAs(view);
    }

    @Test
    void registerDeviceDelegates() {
        DeviceRegisterRequest request = new DeviceRegisterRequest("tok", "ANDROID");
        DeviceRegisterResponse response = mock(DeviceRegisterResponse.class);
        when(portal.registerDevice(request)).thenReturn(response);
        assertThat(controller.registerDevice(request)).isSameAs(response);
    }

    @Test
    void deactivateDeviceReturnsMessage() {
        Map<String, String> body = controller.deactivateDevice(new DeviceRegisterRequest("tok", "ANDROID"));
        verify(portal).deactivateDevice("tok");
        assertThat(body).containsEntry("message", "Device unregistered.");
    }
}
