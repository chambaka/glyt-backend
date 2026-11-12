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
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;

import java.time.Instant;

@Entity
@Table(name = "manifests")
@Getter
@Setter
@NoArgsConstructor
public class Manifest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String manifestDate;

    @Column(nullable = false)
    private String route = "JRO-ZNZ";

    @Column(nullable = false)
    private String airline;

    @Column(nullable = false)
    private String flightNumber;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManifestStatus status = ManifestStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prepared_by_id")
    private UserAccount preparedBy;

    private Instant handoverAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
