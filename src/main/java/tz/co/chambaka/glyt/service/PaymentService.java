package tz.co.chambaka.glyt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.PaymentTransaction;
import tz.co.chambaka.glyt.domain.PaymentWebhookEvent;
import tz.co.chambaka.glyt.domain.Quotation;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.payments.PaymentProvider;
import tz.co.chambaka.glyt.payments.PaymentProviderRegistry;
import tz.co.chambaka.glyt.repo.BookingRepository;
import tz.co.chambaka.glyt.repo.PaymentTransactionRepository;
import tz.co.chambaka.glyt.repo.PaymentWebhookEventRepository;
import tz.co.chambaka.glyt.repo.QuotationRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.security.CurrentUser;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentInitiateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentRefundRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentWebhookRequest;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentTransactionRepository payments;
    private final PaymentWebhookEventRepository webhookEvents;
    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final CustomerAuthService customerAuth;
    private final PaymentProviderRegistry providers;
    private final CodeGenerator codes;
    private final NotificationService notifications;
    private final StaffContext staff;
    private final AppProperties properties;
    private final ObjectMapper json;

    public PaymentService(
            PaymentTransactionRepository payments,
            PaymentWebhookEventRepository webhookEvents,
            BookingRepository bookings,
            QuotationRepository quotations,
            CustomerAuthService customerAuth,
            PaymentProviderRegistry providers,
            CodeGenerator codes,
            NotificationService notifications,
            StaffContext staff,
            AppProperties properties,
            ObjectMapper json
    ) {
        this.payments = payments;
        this.webhookEvents = webhookEvents;
        this.bookings = bookings;
        this.quotations = quotations;
        this.customerAuth = customerAuth;
        this.providers = providers;
        this.codes = codes;
        this.notifications = notifications;
        this.staff = staff;
        this.properties = properties;
        this.json = json;
    }

    @Transactional
    public PaymentView initiate(PaymentInitiateRequest request) {
        CustomerAccount account = customerAuth.requireAccount();
        if (request.quotationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quotationId is required to price the payment.");
        }
        if (request.bookingId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bookingId is required. Create a booking first, then pay to confirm it.");
        }
        String idempotency = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? null
                : request.idempotencyKey().trim();
        if (idempotency != null) {
            var existing = payments.findByIdempotencyKey(idempotency);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        Booking booking = bookings.findById(request.bookingId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking was not found."));
        if (account.getCustomer() == null || !booking.getCustomer().getId().equals(account.getCustomer().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot pay for that booking.");
        }
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only bookings awaiting payment can be charged.");
        }
        if (payments.existsByBookingIdAndStatus(booking.getId(), "SUCCEEDED")) {
            throw new ApiException(HttpStatus.CONFLICT, "That booking is already paid.");
        }

        Quotation quotation = quotations.findById(request.quotationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quotation was not found."));
        if (quotation.getCustomerAccount() != null && !quotation.getCustomerAccount().getId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Quotation belongs to another account.");
        }
        if (quotation.getExpiresAt() != null && quotation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quotation has expired.");
        }
        long amountMinor = quotation.getAmountMinor();
        String currency = quotation.getCurrency();

        PaymentProvider provider = providers.require(request.provider());
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBooking(booking);
        tx.setQuotation(quotation);
        tx.setCustomerAccount(account);
        tx.setProvider(provider.code());
        tx.setIdempotencyKey(idempotency);
        tx.setStatus("PENDING");
        tx.setCurrency(currency);
        tx.setAmountMinor(amountMinor);
        payments.save(tx);

        PaymentProvider.InitiateResult started = provider.initiate(tx);
        tx.setExternalRef(started.externalRef());
        tx.setCheckoutUrl(started.checkoutUrl());
        tx.setProviderPayloadJson(started.providerPayloadJson());
        payments.save(tx);
        return toView(tx);
    }

    @Transactional(readOnly = true)
    public PaymentView get(Long id) {
        PaymentTransaction tx = loadVisible(id);
        return toView(tx);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> mine() {
        return payments.findByCustomerAccountIdOrderByCreatedAtDesc(customerAuth.requireAccount().getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(String status) {
        staff.requireStaff();
        List<PaymentTransaction> rows = status == null || status.isBlank()
                ? payments.findAllByOrderByCreatedAtDesc()
                : payments.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public PaymentView handleWebhook(String providerCode, PaymentWebhookRequest request, String secretHeader) {
        verifyWebhookSecret(secretHeader);
        PaymentProvider provider = providers.require(providerCode);
        String eventId = Texts.required(request.eventId(), "eventId");
        var existingEvent = webhookEvents.findByProviderAndEventId(provider.code(), eventId);
        if (existingEvent.isPresent()) {
            return toView(payments.findByExternalRef(Texts.required(request.externalRef(), "externalRef"))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found for that reference.")));
        }

        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider.code());
        event.setEventId(eventId);
        event.setExternalRef(request.externalRef());
        try {
            event.setPayloadJson(json.writeValueAsString(request));
        } catch (Exception ex) {
            event.setPayloadJson(null);
        }
        webhookEvents.save(event);

        PaymentTransaction tx = payments.findByExternalRef(Texts.required(request.externalRef(), "externalRef"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found for that reference."));
        if (!tx.getProvider().equals(provider.code())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provider mismatch for payment reference.");
        }

        String status = Texts.required(request.status(), "status").toUpperCase(Locale.ROOT);
        if ("SUCCEEDED".equals(status) || "SUCCESS".equals(status) || "PAID".equals(status)) {
            markSucceeded(tx);
        } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            if ("PENDING".equals(tx.getStatus())) {
                tx.setStatus("FAILED");
                tx.setFailureReason(Texts.optional(request.failureReason()));
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported webhook status.");
        }
        event.setProcessed(true);
        return toView(tx);
    }

    @Transactional
    public PaymentView sandboxComplete(Long paymentId, String externalRef) {
        if (!properties.payments().sandbox()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sandbox payment completion is disabled.");
        }
        PaymentTransaction tx = payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found."));
        if (externalRef != null && !externalRef.isBlank() && tx.getExternalRef() != null
                && !tx.getExternalRef().equalsIgnoreCase(externalRef.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment reference does not match.");
        }
        PaymentWebhookRequest fake = new PaymentWebhookRequest(
                "sandbox-" + UUID.randomUUID(),
                tx.getExternalRef(),
                "SUCCEEDED",
                null
        );
        return handleWebhook(tx.getProvider(), fake, properties.payments().webhookSecret());
    }

    @Transactional
    public PaymentView refund(Long id, PaymentRefundRequest request) {
        staff.require(Role.ADMIN);
        PaymentTransaction tx = payments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found."));
        if (!"SUCCEEDED".equals(tx.getStatus()) && !"PARTIALLY_REFUNDED".equals(tx.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only successful payments can be refunded.");
        }
        long already = tx.getRefundAmountMinor() == null ? 0L : tx.getRefundAmountMinor();
        long amount = request.amountMinor() == null ? tx.getAmountMinor() - already : request.amountMinor();
        if (amount < 1 || already + amount > tx.getAmountMinor()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount is invalid.");
        }
        PaymentProvider provider = providers.require(tx.getProvider());
        PaymentProvider.RefundResult result = provider.refund(tx, amount, Texts.optional(request.reason()));
        tx.setRefundAmountMinor(already + amount);
        tx.setRefundReason(Texts.optional(request.reason()));
        tx.setRefundedAt(Instant.now());
        tx.setStatus(already + amount >= tx.getAmountMinor() ? "REFUNDED" : "PARTIALLY_REFUNDED");
        tx.setProviderPayloadJson(result.providerPayloadJson());
        if (tx.getCustomerAccount() != null) {
            notifications.enqueue("EMAIL", tx.getCustomerAccount().getEmail(), "PAYMENT_REFUNDED",
                    "{\"paymentId\":" + tx.getId() + ",\"amountMinor\":" + amount + "}");
        }
        return toView(tx);
    }

    @Transactional(readOnly = true)
    public String reconcileCsv(String status) {
        staff.requireStaff();
        List<PaymentView> rows = list(status);
        StringBuilder csv = new StringBuilder("id,provider,status,currency,amountMinor,externalRef,receiptNumber,bookingId,customerEmail,createdAt,paidAt,refundAmountMinor\n");
        for (PaymentView row : rows) {
            csv.append(row.id()).append(',')
                    .append(row.provider()).append(',')
                    .append(row.status()).append(',')
                    .append(row.currency()).append(',')
                    .append(row.amountMinor()).append(',')
                    .append(csv(row.externalRef())).append(',')
                    .append(csv(row.receiptNumber())).append(',')
                    .append(row.bookingId() == null ? "" : row.bookingId()).append(',')
                    .append(csv(row.customerEmail())).append(',')
                    .append(csv(row.createdAt())).append(',')
                    .append(csv(row.paidAt())).append(',')
                    .append(row.refundAmountMinor() == null ? "" : row.refundAmountMinor())
                    .append('\n');
        }
        return csv.toString();
    }

    private void markSucceeded(PaymentTransaction tx) {
        if ("SUCCEEDED".equals(tx.getStatus()) || "REFUNDED".equals(tx.getStatus()) || "PARTIALLY_REFUNDED".equals(tx.getStatus())) {
            return;
        }
        tx.setStatus("SUCCEEDED");
        tx.setPaidAt(Instant.now());
        tx.setFailureReason(null);
        if (tx.getReceiptNumber() == null) {
            tx.setReceiptNumber(codes.next("receipt", "RC-", 6));
        }
        if (tx.getBooking() != null && "PENDING_PAYMENT".equals(tx.getBooking().getStatus())) {
            tx.getBooking().setStatus("OPEN");
        }
        if (tx.getCustomerAccount() != null) {
            notifications.enqueue("EMAIL", tx.getCustomerAccount().getEmail(), "PAYMENT_RECEIPT",
                    "{\"paymentId\":" + tx.getId() + ",\"receiptNumber\":\"" + tx.getReceiptNumber() + "\"}");
        }
    }

    private PaymentTransaction loadVisible(Long id) {
        PaymentTransaction tx = payments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found."));
        AuthUser principal = CurrentUser.get();
        if (principal.role() == Role.CUSTOMER) {
            CustomerAccount account = customerAuth.requireAccount();
            if (tx.getCustomerAccount() == null || !tx.getCustomerAccount().getId().equals(account.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You cannot view that payment.");
            }
        } else {
            staff.requireStaff();
        }
        return tx;
    }

    private void verifyWebhookSecret(String secretHeader) {
        String expected = properties.payments().webhookSecret();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (secretHeader == null || !expected.equals(secretHeader)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret.");
        }
    }

    private PaymentView toView(PaymentTransaction tx) {
        return new PaymentView(
                tx.getId(),
                tx.getProvider(),
                tx.getStatus(),
                tx.getCurrency(),
                tx.getAmountMinor(),
                tx.getExternalRef(),
                tx.getIdempotencyKey(),
                tx.getCheckoutUrl(),
                tx.getReceiptNumber(),
                tx.getBooking() == null ? null : tx.getBooking().getId(),
                tx.getBooking() == null ? null : tx.getBooking().getBookingNumber(),
                tx.getQuotation() == null ? null : tx.getQuotation().getId(),
                tx.getCustomerAccount() == null ? null : tx.getCustomerAccount().getEmail(),
                tx.getFailureReason(),
                tx.getCreatedAt().toString(),
                tx.getPaidAt() == null ? null : tx.getPaidAt().toString(),
                tx.getRefundAmountMinor(),
                tx.getRefundedAt() == null ? null : tx.getRefundedAt().toString()
        );
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
