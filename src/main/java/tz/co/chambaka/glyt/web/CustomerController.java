package tz.co.chambaka.glyt.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.CustomerBookingService;
import tz.co.chambaka.glyt.service.StaffContext;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRecord;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {

    private final CustomerBookingService service;
    private final StaffContext staff;

    public CustomerController(CustomerBookingService service, StaffContext staff) {
        this.service = service;
        this.staff = staff;
    }

    @GetMapping("/customers")
    public List<CustomerRecord> search(@RequestParam(required = false) String q) {
        return service.searchCustomers(q);
    }

    @PostMapping("/customers")
    public CustomerRecord create(@RequestBody CustomerInput request) {
        return service.createCustomer(request);
    }

    @GetMapping("/customers/{id}")
    public CustomerRecord get(@PathVariable Long id) {
        return service.getCustomer(id);
    }

    @GetMapping("/bookings")
    public List<BookingView> bookings(@RequestParam(required = false) Long customerId) {
        staff.requireStaff();
        return service.listBookings(customerId);
    }

    @PostMapping("/bookings")
    public BookingView createBooking(@RequestBody BookingInput request) {
        return service.createBooking(request);
    }

    @GetMapping("/bookings/{id}")
    public BookingView booking(@PathVariable Long id) {
        staff.requireStaff();
        return service.getBooking(id);
    }
}
