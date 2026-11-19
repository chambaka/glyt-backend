package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Booking;
import tz.co.chambaka.glyt.domain.CustomerAccount;
import tz.co.chambaka.glyt.domain.PaymentTransaction;
import tz.co.chambaka.glyt.repo.PaymentTransactionRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptPdfServiceTest {

    @Mock PaymentTransactionRepository payments;

    private ReceiptPdfService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptPdfService(payments);
    }

    private PaymentTransaction payment(String status) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(5L);
        tx.setProvider("CARD");
        tx.setStatus(status);
        tx.setCurrency("TZS");
        tx.setAmountMinor(150_000);
        when(payments.findById(5L)).thenReturn(Optional.of(tx));
        return tx;
    }

    private static void assertPdf(byte[] bytes) {
        assertThat(bytes).hasSizeGreaterThan(200);
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void minimalSucceededPaymentProducesPdf() {
        payment("SUCCEEDED");
        assertPdf(service.pdf(5L));
    }

    @Test
    void fullyPopulatedPaymentProducesPdf() {
        PaymentTransaction tx = payment("PARTIALLY_REFUNDED");
        tx.setReceiptNumber("RC-000001");
        tx.setExternalRef("CD-REF");
        tx.setPaidAt(Instant.now());
        tx.setRefundAmountMinor(50_000L);
        Booking booking = ServiceTestData.booking(3L, ServiceTestData.customer(1L));
        tx.setBooking(booking);
        CustomerAccount account = new CustomerAccount();
        account.setFullName("Jane Traveler");
        account.setEmail("jane@example.com");
        tx.setCustomerAccount(account);
        assertPdf(service.pdf(5L));
    }

    @Test
    void refundedPaymentProducesPdfAndBlankReferencesRenderAsDash() {
        PaymentTransaction tx = payment("REFUNDED");
        tx.setReceiptNumber("  ");
        tx.setExternalRef(null);
        assertPdf(service.pdf(5L));
    }

    @Test
    void unknownPaymentIsNotFound() {
        when(payments.findById(9L)).thenReturn(Optional.empty());
        ApiException ex = ServiceTestData.expectApi(() -> service.pdf(9L));
        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unpaidStatusesAreRejected() {
        for (String status : new String[]{"PENDING", "FAILED", "CANCELLED"}) {
            payment(status);
            ApiException ex = ServiceTestData.expectApi(() -> service.pdf(5L));
            assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).contains("Receipt is available after a successful payment");
        }
    }

    @Test
    void apiExceptionsRaisedWhileRenderingArePropagated() {
        PaymentTransaction tx = mock(PaymentTransaction.class);
        when(tx.getStatus()).thenReturn("SUCCEEDED");
        when(tx.getProvider()).thenThrow(new ApiException(HttpStatus.CONFLICT, "inner"));
        when(payments.findById(5L)).thenReturn(Optional.of(tx));
        ApiException ex = ServiceTestData.expectApi(() -> service.pdf(5L));
        assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unexpectedRenderingFailuresBecomeServerErrors() {
        PaymentTransaction tx = mock(PaymentTransaction.class);
        when(tx.getStatus()).thenReturn("SUCCEEDED");
        when(tx.getProvider()).thenThrow(new IllegalStateException("boom"));
        when(payments.findById(5L)).thenReturn(Optional.of(tx));
        ApiException ex = ServiceTestData.expectApi(() -> service.pdf(5L));
        assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).contains("Could not build receipt PDF");
    }
}
