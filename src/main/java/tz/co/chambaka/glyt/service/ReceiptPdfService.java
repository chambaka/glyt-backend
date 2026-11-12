package tz.co.chambaka.glyt.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.PaymentTransaction;
import tz.co.chambaka.glyt.repo.PaymentTransactionRepository;
import tz.co.chambaka.glyt.web.ApiException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
public class ReceiptPdfService {

    private final PaymentTransactionRepository payments;

    public ReceiptPdfService(PaymentTransactionRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long paymentId) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment was not found."));
        if (!"SUCCEEDED".equals(payment.getStatus()) && !"REFUNDED".equals(payment.getStatus())
                && !"PARTIALLY_REFUNDED".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Receipt is available after a successful payment.");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5);
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(7, 28, 55));
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(51, 65, 85));
            Paragraph heading = new Paragraph("GoLyt payment receipt", title);
            heading.setAlignment(Element.ALIGN_LEFT);
            document.add(heading);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Receipt " + nullToDash(payment.getReceiptNumber()), body));
            document.add(new Paragraph("Payment #" + payment.getId(), body));
            document.add(new Paragraph("Provider " + payment.getProvider(), body));
            document.add(new Paragraph("Reference " + nullToDash(payment.getExternalRef()), body));
            document.add(new Paragraph("Status " + payment.getStatus(), body));
            document.add(new Paragraph(
                    "Amount " + payment.getCurrency() + " " + String.format("%.2f", payment.getAmountMinor() / 100.0),
                    body
            ));
            if (payment.getBooking() != null) {
                document.add(new Paragraph("Booking " + payment.getBooking().getBookingNumber(), body));
            }
            if (payment.getCustomerAccount() != null) {
                document.add(new Paragraph("Customer " + payment.getCustomerAccount().getFullName(), body));
                document.add(new Paragraph("Email " + payment.getCustomerAccount().getEmail(), body));
            }
            if (payment.getPaidAt() != null) {
                document.add(new Paragraph("Paid at " + payment.getPaidAt(), body));
            }
            if (payment.getRefundAmountMinor() != null) {
                document.add(new Paragraph(
                        "Refunded " + payment.getCurrency() + " " + String.format("%.2f", payment.getRefundAmountMinor() / 100.0),
                        body
                ));
            }
            document.close();
            return out.toByteArray();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build receipt PDF.");
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
