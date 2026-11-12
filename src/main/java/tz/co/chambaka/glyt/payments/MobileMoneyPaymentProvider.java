package tz.co.chambaka.glyt.payments;

import org.springframework.stereotype.Component;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PaymentTransaction;

import java.util.UUID;

@Component
public class MobileMoneyPaymentProvider implements PaymentProvider {

    private final AppProperties properties;

    public MobileMoneyPaymentProvider(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return "MOBILE_MONEY";
    }

    @Override
    public InitiateResult initiate(PaymentTransaction transaction) {
        String ref = "MM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String base = properties.payments().mobileMoneyCheckoutBase();
        String checkout = base + "/complete?ref=" + ref + "&paymentId=" + transaction.getId();
        String payload = "{\"channel\":\"USSD_PUSH\",\"provider\":\"SANDBOX_MM\",\"ref\":\"" + ref + "\"}";
        return new InitiateResult(ref, checkout, payload);
    }

    @Override
    public RefundResult refund(PaymentTransaction transaction, long amountMinor, String reason) {
        String ref = "MMR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return new RefundResult(ref, "{\"refundRef\":\"" + ref + "\",\"amountMinor\":" + amountMinor + "}");
    }
}
