package tz.co.chambaka.glyt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.chambaka.glyt.domain.Airline;
import tz.co.chambaka.glyt.repo.AirlineRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirlineSeederTest {

    @Mock
    AirlineRepository airlines;

    @Test
    void skipsWhenAirlinesAlreadyExist() {
        when(airlines.count()).thenReturn(1L);

        new AirlineSeeder(airlines).run();

        verify(airlines).count();
        verify(airlines, never()).save(any());
        verifyNoMoreInteractions(airlines);
    }

    @Test
    void seedsCatalogWhenEmpty() {
        when(airlines.count()).thenReturn(0L);

        new AirlineSeeder(airlines).run();

        ArgumentCaptor<Airline> captor = ArgumentCaptor.forClass(Airline.class);
        verify(airlines, times(30)).save(captor.capture());
        List<Airline> saved = captor.getAllValues();

        assertThat(saved).extracting(Airline::getCode)
                .startsWith("PW", "TC", "CQ")
                .endsWith("MS", "OTHER")
                .doesNotHaveDuplicates();
        assertThat(saved).allSatisfy(a -> {
            assertThat(a.getCountry()).isEqualTo("TZ");
            assertThat(a.isActive()).isTrue();
            assertThat(a.getName()).isNotBlank();
        });
    }

    @Test
    void sortOrderStartsAtTenAndIncrementsByTen() {
        when(airlines.count()).thenReturn(0L);

        new AirlineSeeder(airlines).run();

        ArgumentCaptor<Airline> captor = ArgumentCaptor.forClass(Airline.class);
        verify(airlines, times(30)).save(captor.capture());
        List<Airline> saved = captor.getAllValues();
        for (int i = 0; i < saved.size(); i++) {
            assertThat(saved.get(i).getSortOrder()).isEqualTo(10 + i * 10);
        }
    }

    @Test
    void containsKeyCarriersWithNames() {
        when(airlines.count()).thenReturn(0L);

        new AirlineSeeder(airlines).run();

        ArgumentCaptor<Airline> captor = ArgumentCaptor.forClass(Airline.class);
        verify(airlines, times(30)).save(captor.capture());
        Set<String> pairs = captor.getAllValues().stream()
                .map(a -> a.getCode() + "=" + a.getName())
                .collect(Collectors.toSet());

        assertThat(pairs).contains(
                "PW=Precision Air",
                "TC=Air Tanzania",
                "KQ=Kenya Airways",
                "QR=Qatar Airways",
                "OTHER=Other / charter");
    }

    @Test
    void codesFitColumnLength() {
        when(airlines.count()).thenReturn(0L);

        new AirlineSeeder(airlines).run();

        ArgumentCaptor<Airline> captor = ArgumentCaptor.forClass(Airline.class);
        verify(airlines, times(30)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(a -> assertThat(a.getCode().length()).isLessThanOrEqualTo(8));
    }
}
