package tz.co.chambaka.glyt.payments;

import tz.co.chambaka.glyt.domain.PaymentTransaction;

public interface PaymentProvider {
    String code();

    InitiateResult initiate(PaymentTransaction transaction);

    RefundResult refund(PaymentTransaction transaction, long amountMinor, String reason);

    record InitiateResult(String externalRef, String checkoutUrl, String providerPayloadJson) {}

    record RefundResult(String externalRef, String providerPayloadJson) {}
}
