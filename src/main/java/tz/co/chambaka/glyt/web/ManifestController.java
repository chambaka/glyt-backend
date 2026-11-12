package tz.co.chambaka.glyt.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.ManifestPdfService;
import tz.co.chambaka.glyt.service.ManifestService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AddBagsRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateManifestRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestView;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manifests")
public class ManifestController {

    private final ManifestService manifests;
    private final ManifestPdfService pdf;

    public ManifestController(ManifestService manifests, ManifestPdfService pdf) {
        this.manifests = manifests;
        this.pdf = pdf;
    }

    @GetMapping
    public List<ManifestView> list() {
        return manifests.list();
    }

    @PostMapping
    public ManifestView create(@RequestBody CreateManifestRequest request) {
        return manifests.create(request);
    }

    @GetMapping("/{id}")
    public ManifestView get(@PathVariable Long id) {
        return manifests.get(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] body = this.pdf.pdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=manifest-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    @PostMapping("/{id}/bags")
    public ManifestView add(@PathVariable Long id, @RequestBody AddBagsRequest request) {
        return manifests.addBags(id, request.bagIds());
    }

    @DeleteMapping("/{id}/bags/{bagId}")
    public ManifestView remove(@PathVariable Long id, @PathVariable Long bagId) {
        return manifests.removeBag(id, bagId);
    }

    @PostMapping("/{id}/finalise")
    public ManifestView finalise(@PathVariable Long id) {
        return manifests.finalise(id);
    }

    @PostMapping("/{id}/handover")
    public ManifestView handover(@PathVariable Long id) {
        return manifests.handover(id);
    }

    @PostMapping("/{id}/missing")
    public ManifestView missing(@PathVariable Long id) {
        return manifests.flagMissing(id);
    }
}
