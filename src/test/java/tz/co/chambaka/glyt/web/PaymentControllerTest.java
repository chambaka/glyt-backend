package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tz.co.chambaka.glyt.service.PaymentService;
import tz.co.chambaka.glyt.service.ReceiptPdfService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentInitiateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentRefundRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentWebhookRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    PaymentService payments;
    @Mock
    ReceiptPdfService receipts;
    @Mock
    PaymentView view;
    @InjectMocks
    PaymentController controller;

    @Test
    void initiateDelegates() {
        PaymentInitiateRequest request = mock(PaymentInitiateRequest.class);
        when(payments.initiate(request)).thenReturn(view);
        assertThat(controller.initiate(request)).isSameAs(view);
    }

    @Test
    void initiatePropagatesErrors() {
        PaymentInitiateRequest request = mock(PaymentInitiateRequest.class);
        when(payments.initiate(request)).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "bad"));
        assertThatThrownBy(() -> controller.initiate(request)).isInstanceOf(ApiException.class);
    }

    @Test
    void mineDelegates() {
        List<PaymentView> rows = List.of(view);
        when(payments.mine()).thenReturn(rows);
        assertThat(controller.mine()).isSameAs(rows);
    }

    @Test
    void listFiltersByStatus() {
        List<PaymentView> rows = List.of(view);
        when(payments.list("PAID")).thenReturn(rows);
        assertThat(controller.list("PAID")).isSameAs(rows);
    }

    @Test
    void getDelegates() {
        when(payments.get(1L)).thenReturn(view);
        assertThat(controller.get(1L)).isSameAs(view);
    }

    @Test
    void receiptChecksAccessThenRendersPdf() {
        byte[] body = {'%', 'P', 'D', 'F'};
        when(payments.get(2L)).thenReturn(view);
        when(receipts.pdf(2L)).thenReturn(body);

        ResponseEntity<byte[]> response = controller.receipt(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=receipt-2.pdf");
        assertThat(response.getBody()).isEqualTo(body);
        var order = inOrder(payments, receipts);
        order.verify(payments).get(2L);
        order.verify(receipts).pdf(2L);
    }

    @Test
    void receiptDeniedWhenPaymentNotVisible() {
        when(payments.get(3L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "missing"));
        assertThatThrownBy(() -> controller.receipt(3L)).isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(receipts);
    }

    @Test
    void refundDelegates() {
        PaymentRefundRequest request = mock(PaymentRefundRequest.class);
        when(payments.refund(1L, request)).thenReturn(view);
        assertThat(controller.refund(1L, request)).isSameAs(view);
    }

    @Test
    void reconcileReturnsCsv() {
        when(payments.reconcileCsv("PAID")).thenReturn("id,status\n1,PAID\n");
        ResponseEntity<byte[]> response = controller.reconcile("PAID");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=payments-reconcile.csv");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("id,status\n1,PAID\n");
    }

    @Test
    void webhookPassesProviderAndSecret() {
        PaymentWebhookRequest request = mock(PaymentWebhookRequest.class);
        when(payments.handleWebhook("mobile-money", request, "s3cret")).thenReturn(view);
        assertThat(controller.webhook("mobile-money", request, "s3cret")).isSameAs(view);
    }

    @Test
    void webhookAllowsMissingSecretAndPropagatesRejection() {
        PaymentWebhookRequest request = mock(PaymentWebhookRequest.class);
        when(payments.handleWebhook("card", request, null))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "bad secret"));
        assertThatThrownBy(() -> controller.webhook("card", request, null))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void sandboxCompletePageWrapsPayment() {
        when(payments.sandboxComplete(5L, "ref")).thenReturn(view);
        Map<String, Object> body = controller.sandboxCompletePage(5L, "ref");
        assertThat(body).containsEntry("message", "Sandbox payment completed.");
        assertThat(body).containsEntry("payment", view);
    }

    @Test
    void sandboxCompletePostReturnsPayment() {
        when(payments.sandboxComplete(5L, null)).thenReturn(view);
        assertThat(controller.sandboxComplete(5L, null)).isSameAs(view);
    }
}
