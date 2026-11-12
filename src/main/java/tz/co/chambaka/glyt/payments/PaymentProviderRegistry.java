package tz.co.chambaka.glyt.payments;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::code, Function.identity()));
    }

    public PaymentProvider require(String code) {
        String key = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        PaymentProvider provider = providers.get(key);
        if (provider == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported payment provider. Use MOBILE_MONEY or CARD.");
        }
        return provider;
    }
}
