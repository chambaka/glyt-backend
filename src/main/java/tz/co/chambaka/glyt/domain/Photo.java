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
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;

import java.time.Instant;

@Entity
@Table(name = "photos")
@Getter
@Setter
@NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bag_id")
    private Bag bag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStage stage;

    @Column(nullable = false)
    private String filePath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captured_by_id")
    private UserAccount capturedBy;

    @Column(nullable = false)
    private Instant capturedAt = Instant.now();
}
