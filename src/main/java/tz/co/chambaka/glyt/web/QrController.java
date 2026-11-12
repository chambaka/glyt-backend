package tz.co.chambaka.glyt.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.QrService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ScanRequest;

@RestController
@RequestMapping("/api/v1/qr")
public class QrController {

    private final QrService qr;

    public QrController(QrService qr) {
        this.qr = qr;
    }

    @PostMapping("/scan")
    public BagDetail scan(@RequestBody ScanRequest request) {
        return qr.scan(request.code());
    }
}
