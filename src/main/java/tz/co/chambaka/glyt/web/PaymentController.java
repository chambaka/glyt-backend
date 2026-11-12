package tz.co.chambaka.glyt.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.PaymentService;
import tz.co.chambaka.glyt.service.ReceiptPdfService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentInitiateRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentRefundRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PaymentWebhookRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;
    private final ReceiptPdfService receipts;

    public PaymentController(PaymentService payments, ReceiptPdfService receipts) {
        this.payments = payments;
        this.receipts = receipts;
    }

    @PostMapping("/initiate")
    public PaymentView initiate(@RequestBody PaymentInitiateRequest request) {
        return payments.initiate(request);
    }

    @GetMapping("/mine")
    public List<PaymentView> mine() {
        return payments.mine();
    }

    @GetMapping
    public List<PaymentView> list(@RequestParam(required = false) String status) {
        return payments.list(status);
    }

    @GetMapping("/{id}")
    public PaymentView get(@PathVariable Long id) {
        return payments.get(id);
    }

    @GetMapping("/{id}/receipt.pdf")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        payments.get(id);
        byte[] body = receipts.pdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @PostMapping("/{id}/refund")
    public PaymentView refund(@PathVariable Long id, @RequestBody PaymentRefundRequest request) {
        return payments.refund(id, request);
    }

    @GetMapping(value = "/reconcile.csv", produces = "text/csv")
    public ResponseEntity<byte[]> reconcile(@RequestParam(required = false) String status) {
        byte[] body = payments.reconcileCsv(status).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments-reconcile.csv")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @PostMapping("/webhooks/{provider}")
    public PaymentView webhook(
            @PathVariable String provider,
            @RequestBody PaymentWebhookRequest request,
            @RequestHeader(value = "X-GoLyt-Webhook-Secret", required = false) String secret
    ) {
        return payments.handleWebhook(provider, request, secret);
    }

    @GetMapping("/sandbox/complete")
    public Map<String, Object> sandboxCompletePage(
            @RequestParam Long paymentId,
            @RequestParam(required = false) String ref
    ) {
        PaymentView view = payments.sandboxComplete(paymentId, ref);
        return Map.of(
                "message", "Sandbox payment completed.",
                "payment", view
        );
    }

    @PostMapping("/sandbox/complete")
    public PaymentView sandboxComplete(
            @RequestParam Long paymentId,
            @RequestParam(required = false) String ref
    ) {
        return payments.sandboxComplete(paymentId, ref);
    }
}
