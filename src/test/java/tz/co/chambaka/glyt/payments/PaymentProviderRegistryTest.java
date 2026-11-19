package tz.co.chambaka.glyt.payments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentProviderRegistryTest {

    private final AppProperties properties = TestFixtures.appProperties();
    private final CardPaymentProvider card = new CardPaymentProvider(properties);
    private final MobileMoneyPaymentProvider mobile = new MobileMoneyPaymentProvider(properties);
    private final PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(card, mobile));

    @Test
    void resolvesProvidersByExactCode() {
        assertThat(registry.require("CARD")).isSameAs(card);
        assertThat(registry.require("MOBILE_MONEY")).isSameAs(mobile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"card", "Card", "  card  ", "mobile_money", " Mobile_Money "})
    void resolvesCaseInsensitivelyAndTrimmed(String code) {
        assertThat(registry.require(code)).isNotNull();
        assertThat(registry.require(code).code()).isEqualTo(code.trim().toUpperCase());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "PAYPAL", "MOBILE-MONEY", "CARDS"})
    void rejectsUnknownCodes(String code) {
        assertThatThrownBy(() -> registry.require(code))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Unsupported payment provider. Use MOBILE_MONEY or CARD.");
                });
    }

    @Test
    void emptyRegistryRejectsEverything() {
        PaymentProviderRegistry empty = new PaymentProviderRegistry(List.of());

        assertThatThrownBy(() -> empty.require("CARD")).isInstanceOf(ApiException.class);
    }

    @Test
    void worksWithMockedProvider() {
        PaymentProvider custom = mock(PaymentProvider.class);
        when(custom.code()).thenReturn("BANK");

        PaymentProviderRegistry custom1 = new PaymentProviderRegistry(List.of(custom));

        assertThat(custom1.require("bank")).isSameAs(custom);
    }

    @Test
    void duplicateCodesFailFast() {
        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(card, new CardPaymentProvider(properties))))
                .isInstanceOf(IllegalStateException.class);
    }
}
