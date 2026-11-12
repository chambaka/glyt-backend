package tz.co.chambaka.glyt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "external_ref", length = 120)
    private String externalRef;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
