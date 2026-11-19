package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.CustomerBookingService;
import tz.co.chambaka.glyt.service.StaffContext;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    CustomerBookingService service;
    @Mock
    StaffContext staff;
    @Mock
    CustomerRecord record;
    @Mock
    BookingView booking;
    @InjectMocks
    CustomerController controller;

    @Test
    void searchDelegates() {
        List<CustomerRecord> rows = List.of(record);
        when(service.searchCustomers("amina")).thenReturn(rows);
        assertThat(controller.search("amina")).isSameAs(rows);
    }

    @Test
    void createDelegates() {
        CustomerInput input = mock(CustomerInput.class);
        when(service.createCustomer(input)).thenReturn(record);
        assertThat(controller.create(input)).isSameAs(record);
    }

    @Test
    void getDelegates() {
        when(service.getCustomer(3L)).thenReturn(record);
        assertThat(controller.get(3L)).isSameAs(record);
    }

    @Test
    void bookingsRequireStaffThenList() {
        List<BookingView> rows = List.of(booking);
        when(service.listBookings(4L)).thenReturn(rows);
        assertThat(controller.bookings(4L)).isSameAs(rows);
        verify(staff).requireStaff();
    }

    @Test
    void bookingsRejectNonStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).requireStaff();
        assertThatThrownBy(() -> controller.bookings(null)).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }

    @Test
    void createBookingDelegates() {
        BookingInput input = mock(BookingInput.class);
        when(service.createBooking(input)).thenReturn(booking);
        assertThat(controller.createBooking(input)).isSameAs(booking);
    }

    @Test
    void bookingRequiresStaffThenFetches() {
        when(service.getBooking(7L)).thenReturn(booking);
        assertThat(controller.booking(7L)).isSameAs(booking);
        verify(staff).requireStaff();
    }

    @Test
    void bookingRejectsNonStaff() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).requireStaff();
        assertThatThrownBy(() -> controller.booking(7L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }
}
