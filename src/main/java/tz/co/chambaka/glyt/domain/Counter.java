package tz.co.chambaka.glyt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "counters")
@Getter
@Setter
@NoArgsConstructor
public class Counter {

    @Id
    private String name;

    private long value;
}
