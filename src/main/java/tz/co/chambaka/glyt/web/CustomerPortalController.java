package tz.co.chambaka.glyt.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerPortalController {

    private final CustomerPortalService portal;

    public CustomerPortalController(CustomerPortalService portal) {
        this.portal = portal;
    }

    @PostMapping("/quotations")
    public QuotationView quote(@RequestBody QuoteRequest request) {
        return portal.createQuote(request, true);
    }

    @GetMapping("/quotations/{id}")
    public QuotationView getQuote(@PathVariable Long id) {
        return portal.getQuote(id);
    }

    @GetMapping("/bookings")
    public List<BookingView> bookings() {
        return portal.myBookings();
    }

    @PostMapping("/bookings")
    public BookingView createBooking(@RequestBody CustomerBookingRequest request) {
        return portal.createBooking(request);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingView cancelBooking(@PathVariable Long id) {
        return portal.cancelBooking(id);
    }

    @GetMapping("/support/tickets")
    public List<SupportTicketView> tickets() {
        return portal.myTickets();
    }

    @PostMapping("/support/tickets")
    public SupportTicketView createTicket(@RequestBody SupportTicketRequest request) {
        return portal.createTicket(request);
    }

    @PostMapping("/devices")
    public DeviceRegisterResponse registerDevice(@RequestBody DeviceRegisterRequest request) {
        return portal.registerDevice(request);
    }

    @PostMapping("/devices/deactivate")
    public Map<String, String> deactivateDevice(@RequestBody DeviceRegisterRequest request) {
        portal.deactivateDevice(request.deviceToken());
        return Map.of("message", "Device unregistered.");
    }
}
