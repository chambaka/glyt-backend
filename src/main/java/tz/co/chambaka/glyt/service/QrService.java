package tz.co.chambaka.glyt.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;

import java.io.ByteArrayOutputStream;

@Service
public class QrService {

    private final BagRepository bags;
    private final BagQueryService queries;

    public QrService(BagRepository bags, BagQueryService queries) {
        this.bags = bags;
        this.queries = queries;
    }

    public byte[] png(Long bagId) {
        Bag bag = bags.findById(bagId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        return encode("golyt:" + bag.getCode());
    }

    public BagDetail scan(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Scan a GoLyt bag code.");
        }
        String code = raw.trim();
        if (code.toLowerCase().startsWith("golyt:")) {
            code = code.substring(6);
        }
        int slash = code.lastIndexOf('/');
        if (code.startsWith("http") && slash > 0) {
            code = code.substring(slash + 1);
        }
        return queries.byCode(code);
    }

    private byte[] encode(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The QR code could not be created.");
        }
    }
}
