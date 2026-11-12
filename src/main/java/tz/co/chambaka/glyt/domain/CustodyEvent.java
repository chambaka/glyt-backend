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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.CustodyEventType;

import java.time.Instant;

@Entity
@Table(name = "custody_events")
@Getter
@Setter
@NoArgsConstructor
public class CustodyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustodyEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false)
    private String location;

    @Column(length = 2000)
    private String notes;

    private String sealNumber;

    @Enumerated(EnumType.STRING)
    private BagStatus previousStatus;

    @Enumerated(EnumType.STRING)
    private BagStatus newStatus;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
