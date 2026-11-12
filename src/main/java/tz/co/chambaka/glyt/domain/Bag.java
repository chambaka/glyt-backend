package tz.co.chambaka.glyt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagCondition;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.BagType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionState;

import java.time.Instant;

@Entity
@Table(name = "bags")
@Getter
@Setter
@NoArgsConstructor
public class Bag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @OneToOne(mappedBy = "bag", fetch = FetchType.LAZY)
    private TrackingToken tracking;

    @OneToOne(mappedBy = "bag", fetch = FetchType.LAZY)
    private ManifestBag manifestLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private BagStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private ExceptionState exceptionState = ExceptionState.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private BagType bagType;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private double weightKg;

    @Column(nullable = false, length = 2000)
    private String description;

    private String originalAirlineTag;

    private String sealNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "bag_condition", columnDefinition = "varchar(20)")
    private BagCondition condition;

    @Column(nullable = false)
    private boolean prohibitedConfirmed = false;

    @Column(nullable = false)
    private String currentLocation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_location_id", unique = true)
    private StorageLocation storageLocation;

    public Manifest getManifest() {
        return manifestLink == null ? null : manifestLink.getManifest();
    }

    public String trackingValue() {
        if (tracking == null || tracking.getRevokedAt() != null) {
            return null;
        }
        return tracking.getToken();
    }

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
