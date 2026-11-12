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
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ExceptionType;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.Priority;

import java.time.Instant;

@Entity
@Table(name = "baggage_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class BaggageException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @Column(nullable = false)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_by_id")
    private UserAccount raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private UserAccount assignedTo;

    @Column(length = 2000)
    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private UserAccount resolvedBy;

    private Instant resolvedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
