package tz.co.chambaka.glyt.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.PhotoService;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final PhotoService photos;

    public MediaController(PhotoService photos) {
        this.photos = photos;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        PhotoService.StoredFile file = photos.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.bytes());
    }
}
