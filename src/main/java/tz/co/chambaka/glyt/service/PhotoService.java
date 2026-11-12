package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Photo;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.storage.ObjectStore;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PhotoView;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class PhotoService {

    private final PhotoRepository photos;
    private final BagRepository bags;
    private final StaffContext staff;
    private final ObjectStore objects;

    public PhotoService(PhotoRepository photos, BagRepository bags, StaffContext staff, ObjectStore objects) {
        this.photos = photos;
        this.bags = bags;
        this.staff = staff;
        this.objects = objects;
    }

    @Transactional
    public PhotoView store(Long bagId, PhotoStage stage, MultipartFile file) {
        staff.requireWriter();
        requireStageRole(stage);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photo is required.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = switch (type) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Photos must be JPEG, PNG, or WebP.");
        };
        Bag bag = bags.findById(bagId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        String key = "photos/" + bag.getCode() + "/" + stage.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "." + ext;
        try {
            objects.put(key, file.getBytes(), type);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The photo could not be saved.");
        }
        Photo photo = new Photo();
        photo.setBag(bag);
        photo.setStage(stage);
        photo.setFilePath(key);
        photo.setCapturedBy(staff.account());
        photos.save(photo);
        return new PhotoView(photo.getId(), photo.getStage().name(), photo.getCapturedAt().toString(), photo.getCapturedBy().getFullName());
    }

    @Transactional(readOnly = true)
    public StoredFile load(Long photoId) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo was not found."));
        String name = photo.getFilePath().toLowerCase(Locale.ROOT);
        String contentType = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new StoredFile(objects.get(photo.getFilePath()), contentType);
    }

    private void requireStageRole(PhotoStage stage) {
        Role role = staff.principal().role();
        if (role == Role.ADMIN) {
            return;
        }
        boolean kiaStage = stage == PhotoStage.KIA_FULL || stage == PhotoStage.KIA_TAG || stage == PhotoStage.KIA_SEAL;
        boolean znzStage = stage == PhotoStage.ZNZ_CONDITION || stage == PhotoStage.ZNZ_SEAL || stage == PhotoStage.RELEASE;
        if (kiaStage && role != Role.KIA_OFFICER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for this action.");
        }
        if (znzStage && role != Role.ZNZ_OFFICER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission for this action.");
        }
    }

    public record StoredFile(byte[] bytes, String contentType) {}
}
