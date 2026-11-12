package tz.co.chambaka.glyt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "airlines")
@Getter
@Setter
@NoArgsConstructor
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IATA code when known (e.g. PW, TC). Unique. */
    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** ISO country focus for the corridor catalog (default TZ). */
    @Column(nullable = false, length = 2)
    private String country = "TZ";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
