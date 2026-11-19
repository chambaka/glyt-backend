package tz.co.chambaka.glyt.payments;

import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PaymentTransaction;
import tz.co.chambaka.glyt.payments.PaymentProvider.InitiateResult;
import tz.co.chambaka.glyt.payments.PaymentProvider.RefundResult;
import tz.co.chambaka.glyt.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;

class MobileMoneyPaymentProviderTest {

    private final AppProperties properties = TestFixtures.appProperties();
    private final MobileMoneyPaymentProvider provider = new MobileMoneyPaymentProvider(properties);

    private static PaymentTransaction tx(long id) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(id);
        return tx;
    }

    @Test
    void codeIsMobileMoney() {
        assertThat(provider.code()).isEqualTo("MOBILE_MONEY");
    }

    @Test
    void initiateBuildsReferenceCheckoutUrlAndPayload() {
        InitiateResult result = provider.initiate(tx(77));

        assertThat(result.externalRef()).matches("MM-[0-9A-F]{16}");
        assertThat(result.checkoutUrl())
                .isEqualTo(properties.payments().mobileMoneyCheckoutBase()
                        + "/complete?ref=" + result.externalRef() + "&paymentId=77");
        assertThat(result.providerPayloadJson())
                .contains("\"channel\":\"USSD_PUSH\"")
                .contains("\"provider\":\"SANDBOX_MM\"")
                .contains("\"ref\":\"" + result.externalRef() + "\"");
    }

    @Test
    void initiateGeneratesUniqueReferences() {
        assertThat(provider.initiate(tx(1)).externalRef()).isNotEqualTo(provider.initiate(tx(1)).externalRef());
    }

    @Test
    void initiateUsesMobileMoneyCheckoutBase() {
        AppProperties custom = new AppProperties(
                properties.jwt(), properties.cors(), properties.storage(), properties.ops(), properties.seed(), properties.mail(),
                new AppProperties.Payments(true, "s", "http://mm.example", "http://card.example"));

        InitiateResult result = new MobileMoneyPaymentProvider(custom).initiate(tx(3));

        assertThat(result.checkoutUrl()).startsWith("http://mm.example/complete?ref=MM-").endsWith("&paymentId=3");
    }

    @Test
    void refundBuildsReferenceAndPayload() {
        RefundResult result = provider.refund(tx(1), 4_000L, "duplicate");

        assertThat(result.externalRef()).matches("MMR-[0-9A-F]{12}");
        assertThat(result.providerPayloadJson())
                .isEqualTo("{\"refundRef\":\"" + result.externalRef() + "\",\"amountMinor\":4000}");
    }

    @Test
    void refundReferencesAreUnique() {
        assertThat(provider.refund(tx(1), 1, "r").externalRef())
                .isNotEqualTo(provider.refund(tx(1), 1, "r").externalRef());
    }
}
