package tz.co.chambaka.glyt.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.service.BagQueryService;
import tz.co.chambaka.glyt.service.BagService;
import tz.co.chambaka.glyt.service.PhotoService;
import tz.co.chambaka.glyt.service.QrService;
import tz.co.chambaka.glyt.service.Texts;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagSummary;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CorrectRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateBagRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PhotoView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReceiveRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ReleaseRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.SealRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.StoreRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ZnzReceiveRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bags")
public class BagController {

    private final BagService bags;
    private final BagQueryService queries;
    private final PhotoService photos;
    private final QrService qr;

    public BagController(BagService bags, BagQueryService queries, PhotoService photos, QrService qr) {
        this.bags = bags;
        this.queries = queries;
        this.photos = photos;
        this.qr = qr;
    }

    @GetMapping
    public List<BagSummary> search(@RequestParam(required = false) String q) {
        return queries.search(q);
    }

    @PostMapping
    public BagDetail create(@RequestBody CreateBagRequest request) {
        return bags.create(request);
    }

    @GetMapping("/{id}")
    public BagDetail get(@PathVariable Long id) {
        return queries.detail(id);
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qr.png(id));
    }

    @GetMapping("/code/{code}")
    public BagDetail byCode(@PathVariable String code) {
        return queries.byCode(code);
    }

    @PostMapping("/{id}/receive")
    public BagDetail receive(@PathVariable Long id, @RequestBody ReceiveRequest request) {
        return bags.receive(id, request);
    }

    @PostMapping("/{id}/seal")
    public BagDetail seal(@PathVariable Long id, @RequestBody SealRequest request) {
        return bags.seal(id, request);
    }

    @PostMapping("/{id}/receive-znz")
    public BagDetail receiveZnz(@PathVariable Long id, @RequestBody ZnzReceiveRequest request) {
        return bags.receiveZnz(id, request);
    }

    @PostMapping("/{id}/store")
    public BagDetail store(@PathVariable Long id, @RequestBody StoreRequest request) {
        return bags.store(id, request.storageCode());
    }

    @PostMapping("/{id}/ready")
    public BagDetail ready(@PathVariable Long id) {
        return bags.ready(id);
    }

    @PostMapping("/{id}/release")
    public BagDetail release(@PathVariable Long id, @RequestBody ReleaseRequest request) {
        return bags.release(id, request);
    }

    @PostMapping("/{id}/cancel")
    public BagDetail cancel(@PathVariable Long id) {
        return bags.cancel(id);
    }

    @PostMapping("/{id}/correct")
    public BagDetail correct(@PathVariable Long id, @RequestBody CorrectRequest request) {
        return bags.correct(id, request);
    }

    @PostMapping("/{id}/photos")
    public PhotoView photo(@PathVariable Long id, @RequestParam String stage, @RequestPart("file") MultipartFile file) {
        return photos.store(id, Texts.parse(PhotoStage.class, stage, "Photo stage"), file);
    }
}
