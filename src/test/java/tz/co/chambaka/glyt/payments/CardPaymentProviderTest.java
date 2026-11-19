package tz.co.chambaka.glyt.payments;

import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PaymentTransaction;
import tz.co.chambaka.glyt.payments.PaymentProvider.InitiateResult;
import tz.co.chambaka.glyt.payments.PaymentProvider.RefundResult;
import tz.co.chambaka.glyt.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;

class CardPaymentProviderTest {

    private final AppProperties properties = TestFixtures.appProperties();
    private final CardPaymentProvider provider = new CardPaymentProvider(properties);

    private static PaymentTransaction tx(long id) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(id);
        return tx;
    }

    @Test
    void codeIsCard() {
        assertThat(provider.code()).isEqualTo("CARD");
    }

    @Test
    void initiateBuildsReferenceCheckoutUrlAndPayload() {
        InitiateResult result = provider.initiate(tx(55));

        assertThat(result.externalRef()).matches("CD-[0-9A-F]{16}");
        assertThat(result.checkoutUrl())
                .isEqualTo(properties.payments().cardCheckoutBase()
                        + "/complete?ref=" + result.externalRef() + "&paymentId=55");
        assertThat(result.providerPayloadJson())
                .contains("\"channel\":\"HOSTED_CHECKOUT\"")
                .contains("\"provider\":\"SANDBOX_CARD\"")
                .contains("\"ref\":\"" + result.externalRef() + "\"");
    }

    @Test
    void initiateGeneratesUniqueReferences() {
        assertThat(provider.initiate(tx(1)).externalRef()).isNotEqualTo(provider.initiate(tx(1)).externalRef());
    }

    @Test
    void initiateUsesCardCheckoutBaseNotMobileBase() {
        AppProperties custom = new AppProperties(
                properties.jwt(), properties.cors(), properties.storage(), properties.ops(), properties.seed(), properties.mail(),
                new AppProperties.Payments(true, "s", "http://mm.example", "http://card.example"));

        InitiateResult result = new CardPaymentProvider(custom).initiate(tx(9));

        assertThat(result.checkoutUrl()).startsWith("http://card.example/complete?ref=CD-").endsWith("&paymentId=9");
    }

    @Test
    void refundBuildsReferenceAndPayload() {
        RefundResult result = provider.refund(tx(1), 12_500L, "customer request");

        assertThat(result.externalRef()).matches("CDR-[0-9A-F]{12}");
        assertThat(result.providerPayloadJson())
                .isEqualTo("{\"refundRef\":\"" + result.externalRef() + "\",\"amountMinor\":12500}");
    }

    @Test
    void refundHandlesZeroAmountAndNullReason() {
        RefundResult result = provider.refund(tx(1), 0L, null);

        assertThat(result.providerPayloadJson()).contains("\"amountMinor\":0");
    }

    @Test
    void implementsPaymentProvider() {
        assertThat(provider).isInstanceOf(PaymentProvider.class);
    }
}
