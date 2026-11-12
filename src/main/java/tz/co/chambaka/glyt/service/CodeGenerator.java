package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tz.co.chambaka.glyt.domain.Counter;
import tz.co.chambaka.glyt.repo.CounterRepository;
import tz.co.chambaka.glyt.web.ApiException;

@Service
public class CodeGenerator {

    private final CounterRepository counters;

    public CodeGenerator(CounterRepository counters) {
        this.counters = counters;
    }

    public String next(String name, String prefix, int width) {
        Counter counter = counters.lockByName(name)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Number sequence is not ready."));
        counter.setValue(counter.getValue() + 1);
        return prefix + String.format("%0" + width + "d", counter.getValue());
    }
}
