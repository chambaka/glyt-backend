package tz.co.chambaka.glyt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id")
    private CustomerAccount customerAccount;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "external_ref", length = 120)
    private String externalRef;

    @Column(name = "idempotency_key", unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 3)
    private String currency = "TZS";

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_amount_minor")
    private Long refundAmountMinor;

    @Column(name = "refund_reason", length = 2000)
    private String refundReason;

    @Column(name = "receipt_number", unique = true, length = 40)
    private String receiptNumber;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "provider_payload_json", columnDefinition = "json")
    private String providerPayloadJson;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
