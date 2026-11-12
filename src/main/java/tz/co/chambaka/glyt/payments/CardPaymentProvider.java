package tz.co.chambaka.glyt.payments;

import org.springframework.stereotype.Component;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PaymentTransaction;

import java.util.UUID;

@Component
public class CardPaymentProvider implements PaymentProvider {

    private final AppProperties properties;

    public CardPaymentProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "CARD";
    }

    @Override
    public InitiateResult initiate(PaymentTransaction transaction) {
        String ref = "CD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String base = properties.payments().cardCheckoutBase();
        String checkout = base + "/complete?ref=" + ref + "&paymentId=" + transaction.getId();
        String payload = "{\"channel\":\"HOSTED_CHECKOUT\",\"provider\":\"SANDBOX_CARD\",\"ref\":\"" + ref + "\"}";
        return new InitiateResult(ref, checkout, payload);
    }

    @Override
    public RefundResult refund(PaymentTransaction transaction, long amountMinor, String reason) {
        String ref = "CDR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new RefundResult(ref, "{\"refundRef\":\"" + ref + "\",\"amountMinor\":" + amountMinor + "}");
    }
}
