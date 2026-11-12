package tz.co.chambaka.glyt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingInput;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BookingView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerBookingRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.DeviceRegisterResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuoteRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.QuotationView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SupportTicketView;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomerPortalService {

    private final CustomerAuthService auth;
    private final ServiceCatalogRepository services;
    private final ServicePriceRepository prices;
    private final QuotationRepository quotations;
    private final BookingRepository bookings;
    private final BagRepository bags;
    private final SupportTicketRepository tickets;
    private final CustomerDeviceTokenRepository devices;
    private final CodeGenerator codes;
    private final NotificationService notifications;
    private final CustomerBookingService bookingViews;
    private final ObjectMapper json;

    public CustomerPortalService(
            CustomerAuthService auth,
            ServiceCatalogRepository services,
            ServicePriceRepository prices,
            QuotationRepository quotations,
            BookingRepository bookings,
            BagRepository bags,
            SupportTicketRepository tickets,
            CustomerDeviceTokenRepository devices,
            CodeGenerator codes,
            NotificationService notifications,
            CustomerBookingService bookingViews,
            ObjectMapper json
    ) {
        this.auth = auth;
        this.services = services;
        this.prices = prices;
        this.quotations = quotations;
        this.bookings = bookings;
        this.bags = bags;
        this.tickets = tickets;
        this.devices = devices;
        this.codes = codes;
        this.notifications = notifications;
        this.bookingViews = bookingViews;
        this.json = json;
    }

    @Transactional
    public QuotationView createQuote(QuoteRequest request, boolean attachCustomer) {
        if (request.serviceId() == null || request.bagCount() == null || request.bagCount() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "serviceId and bagCount are required.");
        }
        ServiceCatalogItem item = services.findById(request.serviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service was not found."));
        ServicePrice price = prices.findFirstByServiceIdAndActiveTrueOrderByIdAsc(item.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No active price for that service."));
        long total = price.getAmountMinor() * request.bagCount();
        Quotation quotation = new Quotation();
        quotation.setService(item);
        if (attachCustomer) {
            quotation.setCustomerAccount(auth.requireAccount());
        }
        quotation.setCurrency(price.getCurrency());
        quotation.setAmountMinor(total);
        quotation.setBagCount(request.bagCount());
        quotation.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("unitAmountMinor", price.getAmountMinor());
            payload.put("unit", price.getUnit());
            payload.put("serviceCode", item.getCode());
            quotation.setPayloadJson(json.writeValueAsString(payload));
        } catch (Exception ex) {
            quotation.setPayloadJson(null);
        }
        quotations.save(quotation);
        return toQuote(quotation);
    }

    @Transactional(readOnly = true)
    public QuotationView getQuote(Long id) {
        Quotation quotation = quotations.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation was not found."));
        CustomerAccount account = maybeAccount();
        if (quotation.getCustomerAccount() != null) {
            if (account == null || !quotation.getCustomerAccount().getId().equals(account.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You cannot view that quotation.");
            }
        }
        return toQuote(quotation);
    }

    @Transactional
    public BookingView createBooking(CustomerBookingRequest request) {
        CustomerAccount account = auth.requireAccount();
        Customer customer = account.getCustomer();
        if (customer == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer profile is incomplete.");
        }
        if (request.quotationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A quotation is required before creating a paid booking.");
        }
        Quotation quotation = quotations.findById(request.quotationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation was not found."));
        if (quotation.getCustomerAccount() != null && !quotation.getCustomerAccount().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Quotation belongs to another account.");
        }
        if (quotation.getExpiresAt() != null && quotation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has expired.");
        }
        BookingInput input = new BookingInput(
                customer.getId(),
                request.internationalArrivalFlight(),
                request.kiaArrivalDate(),
                request.zanzibarTravelDate(),
                request.zanzibarFlight(),
                request.tourOperator(),
                request.bookingReference(),
                request.zanzibarHotel(),
                request.notes()
        );
        Booking booking = new Booking();
        booking.setBookingNumber(codes.next("booking", "BK-", 6));
        booking.setCustomer(customer);
        booking.setStatus("PENDING_PAYMENT");
        booking.setInternationalArrivalFlight(Texts.required(input.internationalArrivalFlight(), "International arrival flight"));
        booking.setKiaArrivalDate(Texts.date(input.kiaArrivalDate(), "KIA arrival date"));
        booking.setZanzibarTravelDate(Texts.date(input.zanzibarTravelDate(), "Zanzibar travel date"));
        booking.setZanzibarFlight(Texts.optional(input.zanzibarFlight()));
        booking.setTourOperator(Texts.optional(input.tourOperator()));
        booking.setBookingReference(Texts.optional(input.bookingReference()));
        booking.setZanzibarHotel(Texts.optional(input.zanzibarHotel()));
        booking.setNotes(Texts.optional(input.notes()));
        bookings.save(booking);
        notifications.enqueue("EMAIL", account.getEmail(), "BOOKING_CREATED",
                "{\"bookingNumber\":\"" + booking.getBookingNumber() + "\"}");
        return bookingViews.getBooking(booking.getId());
    }

    @Transactional(readOnly = true)
    public List<BookingView> myBookings() {
        CustomerAccount account = auth.requireAccount();
        if (account.getCustomer() == null) {
            return List.of();
        }
        return bookingViews.listBookings(account.getCustomer().getId());
    }

    @Transactional
    public BookingView cancelBooking(Long id) {
        CustomerAccount account = auth.requireAccount();
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking was not found."));
        if (account.getCustomer() == null || !booking.getCustomer().getId().equals(account.getCustomer().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot cancel that booking.");
        }
        List<Bag> related = bags.findByBookingId(booking.getId());
        boolean cancellable = related.isEmpty() || related.stream().allMatch(bag -> bag.getStatus() == BagStatus.BOOKED);
        boolean cancellableStatus = "OPEN".equals(booking.getStatus()) || "PENDING_PAYMENT".equals(booking.getStatus());
        if (!cancellableStatus || !cancellable) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only unpaid or open bookings with unprocessed bags can be cancelled.");
        }
        for (Bag bag : related) {
            bag.setStatus(BagStatus.CANCELLED);
            bag.setUpdatedAt(Instant.now());
        }
        booking.setStatus("CANCELLED");
        notifications.enqueue("EMAIL", account.getEmail(), "BOOKING_CANCELLED",
                "{\"bookingNumber\":\"" + booking.getBookingNumber() + "\"}");
        return bookingViews.getBooking(booking.getId());
    }

    @Transactional
    public SupportTicketView createTicket(SupportTicketRequest request) {
        CustomerAccount account = auth.requireAccount();
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerAccount(account);
        ticket.setSubject(Texts.required(request.subject(), "Subject"));
        ticket.setBody(Texts.required(request.body(), "Message"));
        if (request.bagId() != null) {
            Bag bag = bags.findById(request.bagId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
            if (account.getCustomer() == null || !bag.getCustomer().getId().equals(account.getCustomer().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You cannot open a ticket for that bag.");
            }
            ticket.setBag(bag);
        }
        tickets.save(ticket);
        notifications.enqueue("EMAIL", account.getEmail(), "SUPPORT_TICKET_CREATED",
                "{\"ticketId\":" + ticket.getId() + "}");
        return toTicket(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketView> myTickets() {
        return tickets.findByCustomerAccountIdOrderByCreatedAtDesc(auth.requireAccount().getId()).stream()
                .map(this::toTicket)
                .toList();
    }

    @Transactional
    public DeviceRegisterResponse registerDevice(DeviceRegisterRequest request) {
        CustomerAccount account = auth.requireAccount();
        String token = Texts.required(request.deviceToken(), "Device token");
        String platform = Texts.required(request.platform(), "Platform").toUpperCase();
        if (!platform.equals("ANDROID") && !platform.equals("IOS") && !platform.equals("WEB") && !platform.equals("LOCAL")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Platform must be ANDROID, IOS, WEB, or LOCAL.");
        }
        CustomerDeviceToken row = devices.findByDeviceToken(token).orElseGet(CustomerDeviceToken::new);
        row.setCustomerAccount(account);
        row.setDeviceToken(token);
        row.setPlatform(platform);
        row.setActive(true);
        row.setUpdatedAt(Instant.now());
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(Instant.now());
        }
        devices.save(row);
        return new DeviceRegisterResponse(row.getId(), row.getPlatform(), row.isActive());
    }

    @Transactional
    public void deactivateDevice(String deviceToken) {
        CustomerAccount account = auth.requireAccount();
        devices.findByDeviceToken(Texts.required(deviceToken, "Device token")).ifPresent(row -> {
            if (!row.getCustomerAccount().getId().equals(account.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You cannot update that device.");
            }
            row.setActive(false);
            row.setUpdatedAt(Instant.now());
        });
    }

    private CustomerAccount maybeAccount() {
        try {
            return auth.requireAccount();
        } catch (ApiException ex) {
            return null;
        }
    }

    private QuotationView toQuote(Quotation quotation) {
        return new QuotationView(
                quotation.getId(),
                quotation.getService().getId(),
                quotation.getService().getCode(),
                quotation.getService().getName(),
                quotation.getBagCount(),
                quotation.getCurrency(),
                quotation.getAmountMinor(),
                quotation.getExpiresAt() == null ? null : quotation.getExpiresAt().toString(),
                quotation.getCreatedAt().toString()
        );
    }

    private SupportTicketView toTicket(SupportTicket ticket) {
        return new SupportTicketView(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                ticket.getBag() == null ? null : ticket.getBag().getId(),
                ticket.getCreatedAt().toString()
        );
    }
}
