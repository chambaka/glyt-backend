package tz.co.chambaka.glyt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.Customer;
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
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentInitiateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentRefundRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentWebhookRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock PaymentTransactionRepository payments;
    @Mock PaymentWebhookEventRepository webhookEvents;
    @Mock BookingRepository bookings;
    @Mock QuotationRepository quotations;
    @Mock CustomerAuthService customerAuth;
    @Mock PaymentProviderRegistry providers;
    @Mock CodeGenerator codes;
    @Mock NotificationService notifications;
    @Mock StaffContext staff;
    @Mock PaymentProvider provider;

    private AppProperties properties;
    private PaymentService service;
    private CustomerAccount account;
    private Booking booking;
    private Quotation quotation;

    @BeforeEach
    void setUp() {
        properties = TestFixtures.appProperties();
        service = new PaymentService(payments, webhookEvents, bookings, quotations, customerAuth, providers, codes,
                notifications, staff, properties, new ObjectMapper());

        Customer customer = ServiceTestData.customer(1L);
        account = new CustomerAccount();
        account.setId(7L);
        account.setCustomer(customer);
        account.setEmail("jane@example.com");
        account.setFullName("Jane Traveler");
        when(customerAuth.requireAccount()).thenReturn(account);

        booking = ServiceTestData.booking(3L, customer);
        booking.setStatus("PENDING_PAYMENT");
        when(bookings.findById(3L)).thenReturn(Optional.of(booking));

        quotation = new Quotation();
        quotation.setId(4L);
        quotation.setAmountMinor(250_000);
        quotation.setCurrency("TZS");
        quotation.setCustomerAccount(account);
        quotation.setExpiresAt(Instant.now().plusSeconds(600));
        when(quotations.findById(4L)).thenReturn(Optional.of(quotation));

        when(provider.code()).thenReturn("MOBILE_MONEY");
        when(provider.initiate(any())).thenReturn(new PaymentProvider.InitiateResult("MM-REF", "http://pay/complete", "{\"p\":1}"));
        when(provider.refund(any(), anyLong(), any())).thenReturn(new PaymentProvider.RefundResult("MMR-1", "{\"r\":1}"));
        when(providers.require(anyString())).thenReturn(provider);
        when(providers.require(isNull())).thenReturn(provider);
        when(payments.save(any(PaymentTransaction.class))).thenAnswer(ServiceTestData.assignId(11L));
        when(codes.next("receipt", "RC-", 6)).thenReturn("RC-000001");
    }

    @AfterEach
    void tearDown() {
        ServiceTestData.clearAuth();
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    private PaymentTransaction tx(String status) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(11L);
        tx.setBooking(booking);
        tx.setQuotation(quotation);
        tx.setCustomerAccount(account);
        tx.setProvider("MOBILE_MONEY");
        tx.setExternalRef("MM-REF");
        tx.setStatus(status);
        tx.setCurrency("TZS");
        tx.setAmountMinor(250_000);
        return tx;
    }

    private PaymentInitiateRequest initiateRequest(String key) {
        return new PaymentInitiateRequest("MOBILE_MONEY", 3L, 4L, null, "TZS", key);
    }

    // ---- initiate ----

    @Test
    void initiateCreatesPendingTransactionAndStartsProvider() {
        PaymentView view = service.initiate(initiateRequest("  key-1 "));

        ArgumentCaptor<PaymentTransaction> saved = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(payments, org.mockito.Mockito.times(2)).save(saved.capture());
        PaymentTransaction tx = saved.getValue();
        assertThat(tx.getStatus()).isEqualTo("PENDING");
        assertThat(tx.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(tx.getAmountMinor()).isEqualTo(250_000);
        assertThat(tx.getExternalRef()).isEqualTo("MM-REF");
        assertThat(tx.getCheckoutUrl()).isEqualTo("http://pay/complete");
        assertThat(tx.getProviderPayloadJson()).isEqualTo("{\"p\":1}");
        assertThat(view.provider()).isEqualTo("MOBILE_MONEY");
        assertThat(view.bookingNumber()).isEqualTo("BK-3");
        assertThat(view.quotationId()).isEqualTo(4L);
        assertThat(view.customerEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void initiateWithBlankIdempotencyKeySkipsLookup() {
        PaymentView view = service.initiate(initiateRequest("  "));
        assertThat(view.idempotencyKey()).isNull();
        service.initiate(initiateRequest(null));
        verify(payments, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void initiateReturnsExistingTransactionForRepeatedIdempotencyKey() {
        PaymentTransaction existing = tx("PENDING");
        existing.setIdempotencyKey("key-1");
        when(payments.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        PaymentView view = service.initiate(initiateRequest("key-1"));

        assertThat(view.id()).isEqualTo(11L);
        verify(bookings, never()).findById(any());
        verify(provider, never()).initiate(any());
    }

    @Test
    void initiateValidatesRequest() {
        api(() -> service.initiate(new PaymentInitiateRequest("CARD", 3L, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "quotationId is required");
        api(() -> service.initiate(new PaymentInitiateRequest("CARD", null, 4L, null, null, null)),
                HttpStatus.BAD_REQUEST, "bookingId is required");
    }

    @Test
    void initiateRejectsUnknownBooking() {
        when(bookings.findById(99L)).thenReturn(Optional.empty());
        api(() -> service.initiate(new PaymentInitiateRequest("CARD", 99L, 4L, null, null, null)),
                HttpStatus.NOT_FOUND, "Booking was not found");
    }

    @Test
    void initiateRejectsBookingOfAnotherCustomer() {
        booking.setCustomer(ServiceTestData.customer(2L));
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.FORBIDDEN, "cannot pay");
    }

    @Test
    void initiateRejectsAccountWithoutCustomerProfile() {
        account.setCustomer(null);
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.FORBIDDEN, "cannot pay");
    }

    @Test
    void initiateRejectsBookingNotAwaitingPayment() {
        booking.setStatus("OPEN");
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.BAD_REQUEST, "awaiting payment");
    }

    @Test
    void initiateRejectsAlreadyPaidBooking() {
        when(payments.existsByBookingIdAndStatus(3L, "SUCCEEDED")).thenReturn(true);
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.CONFLICT, "already paid");
    }

    @Test
    void initiateRejectsUnknownQuotation() {
        when(quotations.findById(5L)).thenReturn(Optional.empty());
        api(() -> service.initiate(new PaymentInitiateRequest("CARD", 3L, 5L, null, null, null)),
                HttpStatus.NOT_FOUND, "Quotation was not found");
    }

    @Test
    void initiateRejectsQuotationOfAnotherAccount() {
        CustomerAccount other = new CustomerAccount();
        other.setId(99L);
        quotation.setCustomerAccount(other);
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.FORBIDDEN, "another account");
    }

    @Test
    void initiateAcceptsAnonymousQuotationWithoutExpiry() {
        quotation.setCustomerAccount(null);
        quotation.setExpiresAt(null);
        assertThat(service.initiate(initiateRequest(null)).status()).isEqualTo("PENDING");
    }

    @Test
    void initiateRejectsExpiredQuotation() {
        quotation.setExpiresAt(Instant.now().minusSeconds(5));
        api(() -> service.initiate(initiateRequest(null)), HttpStatus.BAD_REQUEST, "expired");
    }

    @Test
    void initiatePropagatesUnsupportedProvider() {
        when(providers.require("PAYPAL")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Unsupported payment provider."));
        api(() -> service.initiate(new PaymentInitiateRequest("PAYPAL", 3L, 4L, null, null, null)),
                HttpStatus.BAD_REQUEST, "Unsupported payment provider");
    }

    // ---- get / mine / list ----

    @Test
    void getAsCustomerOwningPayment() {
        ServiceTestData.authenticate(new AuthUser(7L, "jane@example.com", "Jane", Role.CUSTOMER, null, 0, false));
        when(payments.findById(11L)).thenReturn(Optional.of(tx("PENDING")));
        assertThat(service.get(11L).id()).isEqualTo(11L);
    }

    @Test
    void getAsCustomerRejectsForeignOrOrphanPayments() {
        ServiceTestData.authenticate(new AuthUser(7L, "jane@example.com", "Jane", Role.CUSTOMER, null, 0, false));
        PaymentTransaction foreign = tx("PENDING");
        CustomerAccount other = new CustomerAccount();
        other.setId(99L);
        foreign.setCustomerAccount(other);
        when(payments.findById(12L)).thenReturn(Optional.of(foreign));
        api(() -> service.get(12L), HttpStatus.FORBIDDEN, "cannot view");

        PaymentTransaction orphan = tx("PENDING");
        orphan.setCustomerAccount(null);
        when(payments.findById(13L)).thenReturn(Optional.of(orphan));
        api(() -> service.get(13L), HttpStatus.FORBIDDEN, "cannot view");
    }

    @Test
    void getAsStaffRequiresStaffCredentials() {
        ServiceTestData.authenticate(TestFixtures.staff(Role.ADMIN, "KIA"));
        when(payments.findById(11L)).thenReturn(Optional.of(tx("PENDING")));
        assertThat(service.get(11L).status()).isEqualTo("PENDING");
        verify(staff).requireStaff();
    }

    @Test
    void getUnknownPayment() {
        when(payments.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.get(404L), HttpStatus.NOT_FOUND, "Payment was not found");
    }

    @Test
    void mineListsPaymentsForCurrentAccount() {
        when(payments.findByCustomerAccountIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(tx("PENDING"), tx("SUCCEEDED")));
        assertThat(service.mine()).hasSize(2);
    }

    @Test
    void listFiltersByStatus() {
        when(payments.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tx("PENDING")));
        when(payments.findByStatusOrderByCreatedAtDesc("SUCCEEDED")).thenReturn(List.of());
        assertThat(service.list(null)).hasSize(1);
        assertThat(service.list(" ")).hasSize(1);
        assertThat(service.list(" succeeded ")).isEmpty();
        verify(staff, org.mockito.Mockito.times(3)).requireStaff();
    }

    // ---- webhook ----

    private PaymentWebhookRequest hook(String status) {
        return new PaymentWebhookRequest("evt-1", "MM-REF", status, "declined");
    }

    @Test
    void webhookSuccessMarksPaymentSucceededAndConfirmsBooking() {
        PaymentTransaction tx = tx("PENDING");
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));

        PaymentView view = service.handleWebhook("MOBILE_MONEY", hook("paid"), "test-webhook-secret");

        assertThat(view.status()).isEqualTo("SUCCEEDED");
        assertThat(tx.getReceiptNumber()).isEqualTo("RC-000001");
        assertThat(tx.getPaidAt()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo("OPEN");
        verify(notifications).enqueue(eq("EMAIL"), eq("jane@example.com"), eq("PAYMENT_RECEIPT"), anyString());
        ArgumentCaptor<PaymentWebhookEvent> event = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(webhookEvents).save(event.capture());
        assertThat(event.getValue().isProcessed()).isTrue();
        assertThat(event.getValue().getPayloadJson()).contains("evt-1");
    }

    @Test
    void webhookAcceptsSuccessAliases() {
        for (String alias : new String[]{"SUCCEEDED", "success"}) {
            PaymentTransaction tx = tx("PENDING");
            when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));
            assertThat(service.handleWebhook("MOBILE_MONEY", hook(alias), "test-webhook-secret").status()).isEqualTo("SUCCEEDED");
        }
    }

    @Test
    void webhookSuccessIsIdempotentForSettledPayments() {
        for (String settled : new String[]{"SUCCEEDED", "REFUNDED", "PARTIALLY_REFUNDED"}) {
            PaymentTransaction tx = tx(settled);
            when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));
            assertThat(service.handleWebhook("MOBILE_MONEY", hook("SUCCEEDED"), "test-webhook-secret").status()).isEqualTo(settled);
            assertThat(tx.getReceiptNumber()).isNull();
        }
        verify(notifications, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void webhookSuccessKeepsExistingReceiptAndSkipsOptionalSideEffects() {
        PaymentTransaction tx = tx("PENDING");
        tx.setReceiptNumber("RC-OLD");
        tx.setCustomerAccount(null);
        booking.setStatus("OPEN");
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));

        service.handleWebhook("MOBILE_MONEY", hook("SUCCEEDED"), "test-webhook-secret");

        assertThat(tx.getReceiptNumber()).isEqualTo("RC-OLD");
        verify(codes, never()).next(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(notifications, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void webhookSuccessWithoutBooking() {
        PaymentTransaction tx = tx("PENDING");
        tx.setBooking(null);
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));
        assertThat(service.handleWebhook("MOBILE_MONEY", hook("SUCCEEDED"), "test-webhook-secret").bookingId()).isNull();
    }

    @Test
    void webhookFailureMarksPendingPaymentFailed() {
        PaymentTransaction tx = tx("PENDING");
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));

        PaymentView view = service.handleWebhook("MOBILE_MONEY", hook("failed"), "test-webhook-secret");

        assertThat(view.status()).isEqualTo("FAILED");
        assertThat(view.failureReason()).isEqualTo("declined");
    }

    @Test
    void webhookFailureDoesNotOverrideNonPendingPayment() {
        PaymentTransaction tx = tx("SUCCEEDED");
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));
        assertThat(service.handleWebhook("MOBILE_MONEY", hook("CANCELLED"), "test-webhook-secret").status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void webhookRejectsUnsupportedOrMissingStatus() {
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx("PENDING")));
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("weird"), "test-webhook-secret"), HttpStatus.BAD_REQUEST, "Unsupported webhook status");
        api(() -> service.handleWebhook("MOBILE_MONEY", hook(null), "test-webhook-secret"), HttpStatus.BAD_REQUEST, "status is required");
    }

    @Test
    void webhookVerifiesSecret() {
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("PAID"), null), HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("PAID"), "wrong"), HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
        verify(webhookEvents, never()).save(any());
    }

    @Test
    void webhookSkipsSecretCheckWhenNotConfigured() {
        AppProperties open = ServiceTestData.withPayments(new AppProperties.Payments(true, " ", "a", "b"));
        PaymentService noSecret = new PaymentService(payments, webhookEvents, bookings, quotations, customerAuth, providers,
                codes, notifications, staff, open, new ObjectMapper());
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx("PENDING")));
        assertThat(noSecret.handleWebhook("MOBILE_MONEY", hook("PAID"), null).status()).isEqualTo("SUCCEEDED");

        AppProperties nullSecret = ServiceTestData.withPayments(new AppProperties.Payments(true, null, "a", "b"));
        PaymentService nullSecretService = new PaymentService(payments, webhookEvents, bookings, quotations, customerAuth,
                providers, codes, notifications, staff, nullSecret, new ObjectMapper());
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx("PENDING")));
        assertThat(nullSecretService.handleWebhook("MOBILE_MONEY", hook("PAID"), "anything").status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void webhookRequiresEventId() {
        api(() -> service.handleWebhook("MOBILE_MONEY", new PaymentWebhookRequest(" ", "MM-REF", "PAID", null), "test-webhook-secret"),
                HttpStatus.BAD_REQUEST, "eventId is required");
    }

    @Test
    void webhookReplayReturnsCurrentPayment() {
        when(webhookEvents.findByProviderAndEventId("MOBILE_MONEY", "evt-1")).thenReturn(Optional.of(new PaymentWebhookEvent()));
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx("SUCCEEDED")));

        assertThat(service.handleWebhook("MOBILE_MONEY", hook("PAID"), "test-webhook-secret").status()).isEqualTo("SUCCEEDED");
        verify(webhookEvents, never()).save(any());
    }

    @Test
    void webhookReplayFailsForUnknownOrMissingReference() {
        when(webhookEvents.findByProviderAndEventId("MOBILE_MONEY", "evt-1")).thenReturn(Optional.of(new PaymentWebhookEvent()));
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.empty());
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("PAID"), "test-webhook-secret"), HttpStatus.NOT_FOUND, "Payment was not found");
        api(() -> service.handleWebhook("MOBILE_MONEY", new PaymentWebhookRequest("evt-1", " ", "PAID", null), "test-webhook-secret"),
                HttpStatus.BAD_REQUEST, "externalRef is required");
    }

    @Test
    void webhookRejectsUnknownPaymentReference() {
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.empty());
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("PAID"), "test-webhook-secret"), HttpStatus.NOT_FOUND, "Payment was not found");
        api(() -> service.handleWebhook("MOBILE_MONEY", new PaymentWebhookRequest("evt-2", null, "PAID", null), "test-webhook-secret"),
                HttpStatus.BAD_REQUEST, "externalRef is required");
    }

    @Test
    void webhookRejectsProviderMismatch() {
        PaymentTransaction tx = tx("PENDING");
        tx.setProvider("CARD");
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));
        api(() -> service.handleWebhook("MOBILE_MONEY", hook("PAID"), "test-webhook-secret"), HttpStatus.BAD_REQUEST, "Provider mismatch");
    }

    @Test
    void webhookToleratesPayloadSerialisationFailure() throws Exception {
        ObjectMapper broken = org.mockito.Mockito.mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new IllegalStateException("boom"));
        PaymentService brokenJson = new PaymentService(payments, webhookEvents, bookings, quotations, customerAuth, providers,
                codes, notifications, staff, properties, broken);
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx("PENDING")));

        brokenJson.handleWebhook("MOBILE_MONEY", hook("PAID"), "test-webhook-secret");

        ArgumentCaptor<PaymentWebhookEvent> event = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(webhookEvents).save(event.capture());
        assertThat(event.getValue().getPayloadJson()).isNull();
    }

    // ---- sandbox complete ----

    @Test
    void sandboxCompleteMarksPaymentPaid() {
        PaymentTransaction tx = tx("PENDING");
        when(payments.findById(11L)).thenReturn(Optional.of(tx));
        when(payments.findByExternalRef("MM-REF")).thenReturn(Optional.of(tx));

        assertThat(service.sandboxComplete(11L, " mm-ref ").status()).isEqualTo("SUCCEEDED");
        assertThat(service.sandboxComplete(11L, null).status()).isEqualTo("SUCCEEDED");
        assertThat(service.sandboxComplete(11L, "  ").status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void sandboxCompleteWithoutStoredExternalRefSkipsReferenceCheck() {
        PaymentTransaction tx = tx("PENDING");
        tx.setExternalRef(null);
        when(payments.findById(11L)).thenReturn(Optional.of(tx));
        // handleWebhook then fails because the fake request carries no externalRef
        api(() -> service.sandboxComplete(11L, "whatever"), HttpStatus.BAD_REQUEST, "externalRef is required");
    }

    @Test
    void sandboxCompleteRejectsWhenDisabledMissingOrMismatched() {
        AppProperties disabled = ServiceTestData.withPayments(new AppProperties.Payments(false, "s", "a", "b"));
        PaymentService prod = new PaymentService(payments, webhookEvents, bookings, quotations, customerAuth, providers,
                codes, notifications, staff, disabled, new ObjectMapper());
        api(() -> prod.sandboxComplete(11L, null), HttpStatus.FORBIDDEN, "disabled");

        when(payments.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.sandboxComplete(404L, null), HttpStatus.NOT_FOUND, "Payment was not found");

        when(payments.findById(11L)).thenReturn(Optional.of(tx("PENDING")));
        api(() -> service.sandboxComplete(11L, "OTHER"), HttpStatus.BAD_REQUEST, "does not match");
    }

    // ---- refund ----

    @Test
    void fullRefundMarksPaymentRefunded() {
        PaymentTransaction tx = tx("SUCCEEDED");
        when(payments.findById(11L)).thenReturn(Optional.of(tx));

        PaymentView view = service.refund(11L, new PaymentRefundRequest(null, " customer request "));

        assertThat(view.status()).isEqualTo("REFUNDED");
        assertThat(view.refundAmountMinor()).isEqualTo(250_000L);
        assertThat(tx.getRefundReason()).isEqualTo("customer request");
        assertThat(tx.getProviderPayloadJson()).isEqualTo("{\"r\":1}");
        verify(staff).require(Role.ADMIN);
        verify(provider).refund(tx, 250_000L, "customer request");
        verify(notifications).enqueue(eq("EMAIL"), eq("jane@example.com"), eq("PAYMENT_REFUNDED"), anyString());
    }

    @Test
    void partialRefundThenRemainder() {
        PaymentTransaction tx = tx("SUCCEEDED");
        when(payments.findById(11L)).thenReturn(Optional.of(tx));

        assertThat(service.refund(11L, new PaymentRefundRequest(100_000L, null)).status()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(tx.getRefundReason()).isNull();
        assertThat(service.refund(11L, new PaymentRefundRequest(null, null)).refundAmountMinor()).isEqualTo(250_000L);
        assertThat(tx.getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void refundWithoutCustomerAccountSkipsNotification() {
        PaymentTransaction tx = tx("SUCCEEDED");
        tx.setCustomerAccount(null);
        when(payments.findById(11L)).thenReturn(Optional.of(tx));
        service.refund(11L, new PaymentRefundRequest(1L, null));
        verify(notifications, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void refundRejectsInvalidStateAndAmounts() {
        when(payments.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.refund(404L, new PaymentRefundRequest(1L, null)), HttpStatus.NOT_FOUND, "Payment was not found");

        when(payments.findById(11L)).thenReturn(Optional.of(tx("PENDING")));
        api(() -> service.refund(11L, new PaymentRefundRequest(1L, null)), HttpStatus.BAD_REQUEST, "Only successful payments");

        when(payments.findById(12L)).thenReturn(Optional.of(tx("SUCCEEDED")));
        api(() -> service.refund(12L, new PaymentRefundRequest(0L, null)), HttpStatus.BAD_REQUEST, "Refund amount is invalid");
        api(() -> service.refund(12L, new PaymentRefundRequest(250_001L, null)), HttpStatus.BAD_REQUEST, "Refund amount is invalid");

        PaymentTransaction fullyRefundedButPartial = tx("PARTIALLY_REFUNDED");
        fullyRefundedButPartial.setRefundAmountMinor(250_000L);
        when(payments.findById(13L)).thenReturn(Optional.of(fullyRefundedButPartial));
        api(() -> service.refund(13L, new PaymentRefundRequest(null, null)), HttpStatus.BAD_REQUEST, "Refund amount is invalid");
        verify(provider, never()).refund(any(), anyLong(), any());
    }

    @Test
    void refundRequiresAdmin() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "no")).when(staff).require(Role.ADMIN);
        api(() -> service.refund(11L, new PaymentRefundRequest(1L, null)), HttpStatus.FORBIDDEN, "no");
    }

    // ---- reconcile ----

    @Test
    void reconcileCsvListsPayments() {
        PaymentTransaction full = tx("SUCCEEDED");
        full.setReceiptNumber("RC-1");
        full.setPaidAt(Instant.parse("2026-01-01T10:00:00Z"));
        full.setRefundAmountMinor(500L);
        full.setExternalRef("ref \"quoted\"");
        PaymentTransaction bare = tx("PENDING");
        bare.setBooking(null);
        bare.setCustomerAccount(null);
        bare.setExternalRef(null);
        when(payments.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(full, bare));

        String csv = service.reconcileCsv(null);

        assertThat(csv).startsWith("id,provider,status,currency,amountMinor,externalRef,receiptNumber,bookingId,customerEmail,createdAt,paidAt,refundAmountMinor\n");
        assertThat(csv).contains("11,MOBILE_MONEY,SUCCEEDED,TZS,250000,\"ref \"\"quoted\"\"\",\"RC-1\",3,\"jane@example.com\"");
        assertThat(csv).contains("11,MOBILE_MONEY,PENDING,TZS,250000,,,,,\"");
        assertThat(csv).contains(",500\n");
        verify(staff, org.mockito.Mockito.atLeastOnce()).requireStaff();
    }
}
