package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Counter;
import tz.co.chambaka.glyt.repo.CounterRepository;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeGeneratorTest {

    @Mock
    CounterRepository counters;

    @InjectMocks
    CodeGenerator generator;

    private Counter counter(String name, long value) {
        Counter c = new Counter();
        c.setName(name);
        c.setValue(value);
        return c;
    }

    @Test
    void incrementsAndFormatsWithPadding() {
        Counter c = counter("bag", 0);
        when(counters.lockByName("bag")).thenReturn(Optional.of(c));

        assertThat(generator.next("bag", "BAG-", 5)).isEqualTo("BAG-00001");
        assertThat(c.getValue()).isEqualTo(1);
        verify(counters).lockByName("bag");
    }

    @Test
    void successiveCallsKeepIncrementing() {
        Counter c = counter("manifest", 41);
        when(counters.lockByName("manifest")).thenReturn(Optional.of(c));

        assertThat(generator.next("manifest", "M", 3)).isEqualTo("M042");
        assertThat(generator.next("manifest", "M", 3)).isEqualTo("M043");
        assertThat(c.getValue()).isEqualTo(43);
    }

    @Test
    void widthSmallerThanValueDoesNotTruncate() {
        Counter c = counter("receipt", 12344);
        when(counters.lockByName("receipt")).thenReturn(Optional.of(c));

        assertThat(generator.next("receipt", "R", 2)).isEqualTo("R12345");
    }

    @Test
    void emptyPrefixAllowed() {
        Counter c = counter("x", 0);
        when(counters.lockByName("x")).thenReturn(Optional.of(c));

        assertThat(generator.next("x", "", 4)).isEqualTo("0001");
    }

    @Test
    void missingCounterThrowsServerError() {
        when(counters.lockByName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generator.next("ghost", "G", 4))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("Number sequence is not ready.");
                });
    }
}
