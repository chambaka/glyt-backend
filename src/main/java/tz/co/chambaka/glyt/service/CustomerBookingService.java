package tz.co.chambaka.glyt.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.Customer;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.CustomerRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRecord;

import java.util.List;

@Service
public class CustomerBookingService {

    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final CodeGenerator codes;
    private final StaffContext staff;

    public CustomerBookingService(CustomerRepository customers, BookingRepository bookings, CodeGenerator codes, StaffContext staff) {
        this.customers = customers;
        this.bookings = bookings;
        this.codes = codes;
        this.staff = staff;
    }

    @Transactional
    public CustomerRecord createCustomer(CustomerInput input) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        Customer customer = new Customer();
        customer.setCustomerNumber(codes.next("customer", "CU-", 6));
        customer.setFullName(Texts.required(input.fullName(), "Full name"));
        customer.setPhone(Texts.required(input.phone(), "Mobile number"));
        customer.setEmail(Texts.required(input.email(), "Email"));
        customer.setNationality(Texts.required(input.nationality(), "Nationality"));
        customer.setPassportNumber(Texts.required(input.passportNumber(), "Passport number"));
        customers.save(customer);
        return toCustomer(customer);
    }

    @Transactional(readOnly = true)
    public CustomerRecord getCustomer(Long id) {
        staff.requireStaff();
        return toCustomer(customers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer was not found.")));
    }

    @Transactional(readOnly = true)
    public List<CustomerRecord> searchCustomers(String q) {
        staff.requireStaff();
        String term = q == null ? "" : q.trim().toLowerCase();
        if (term.isEmpty()) {
            return customers.findAll().stream().limit(20).map(this::toCustomer).toList();
        }
        return customers.search("%" + term + "%", PageRequest.of(0, 30)).stream().map(this::toCustomer).toList();
    }

    @Transactional
    public BookingView createBooking(BookingInput input) {
        staff.require(Role.ADMIN, Role.KIA_OFFICER);
        if (input.customerId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer is required.");
        }
        Customer customer = customers.findById(input.customerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer was not found."));
        Booking booking = new Booking();
        booking.setBookingNumber(codes.next("booking", "BK-", 6));
        booking.setCustomer(customer);
        booking.setStatus("OPEN");
        booking.setInternationalArrivalFlight(Texts.required(input.internationalArrivalFlight(), "International arrival flight"));
        booking.setKiaArrivalDate(Texts.date(input.kiaArrivalDate(), "KIA arrival date"));
        booking.setZanzibarTravelDate(Texts.date(input.zanzibarTravelDate(), "Zanzibar travel date"));
        booking.setZanzibarFlight(Texts.optional(input.zanzibarFlight()));
        booking.setTourOperator(Texts.optional(input.tourOperator()));
        booking.setBookingReference(Texts.optional(input.bookingReference()));
        booking.setZanzibarHotel(Texts.optional(input.zanzibarHotel()));
        booking.setNotes(Texts.optional(input.notes()));
        bookings.save(booking);
        return toBooking(booking);
    }

    @Transactional(readOnly = true)
    public BookingView getBooking(Long id) {
        return toBooking(bookings.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking was not found.")));
    }

    @Transactional(readOnly = true)
    public List<BookingView> listBookings(Long customerId) {
        List<Booking> rows = customerId == null ? bookings.findAll() : bookings.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return rows.stream().map(this::toBooking).toList();
    }

    private CustomerRecord toCustomer(Customer customer) {
        return new CustomerRecord(customer.getId(), customer.getCustomerNumber(), customer.getFullName(), customer.getPhone(), customer.getEmail(), customer.getNationality(), customer.getPassportNumber());
    }

    private BookingView toBooking(Booking booking) {
        return new BookingView(
                booking.getId(),
                booking.getBookingNumber(),
                booking.getCustomer().getId(),
                booking.getCustomer().getFullName(),
                booking.getStatus(),
                booking.getInternationalArrivalFlight(),
                booking.getKiaArrivalDate(),
                booking.getZanzibarTravelDate(),
                booking.getZanzibarFlight(),
                booking.getTourOperator(),
                booking.getBookingReference(),
                booking.getZanzibarHotel(),
                booking.getNotes()
        );
    }
}
