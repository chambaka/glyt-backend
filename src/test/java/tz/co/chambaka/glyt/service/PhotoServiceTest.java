package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Photo;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.repo.PhotoRepository;
import tz.co.chambaka.glyt.storage.ObjectStore;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PhotoView;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhotoServiceTest {

    @Mock PhotoRepository photos;
    @Mock BagRepository bags;
    @Mock StaffContext staff;
    @Mock ObjectStore objects;

    private PhotoService service;
    private Bag bag;

    @BeforeEach
    void setUp() {
        service = new PhotoService(photos, bags, staff, objects);
        bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        when(bags.findById(1L)).thenReturn(Optional.of(bag));
        when(photos.save(any(Photo.class))).thenAnswer(ServiceTestData.assignId(55L));
        as(Role.ADMIN);
    }

    private UserAccount as(Role role) {
        UserAccount user = TestFixtures.staff(role, "KIA");
        when(staff.account()).thenReturn(user);
        when(staff.principal()).thenReturn(TestFixtures.authUser(user));
        return user;
    }

    private static MockMultipartFile file(String contentType) {
        return new MockMultipartFile("file", "p.bin", contentType, new byte[]{1, 2, 3});
    }

    private static void api(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    // ---- store ----

    @Test
    void storeSavesObjectAndPhotoRow() {
        PhotoView view = service.store(1L, PhotoStage.KIA_FULL, file("image/jpeg"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objects).put(key.capture(), eq(new byte[]{1, 2, 3}), eq("image/jpeg"));
        assertThat(key.getValue()).startsWith("photos/GL-1/kia_full-").endsWith(".jpg");
        ArgumentCaptor<Photo> saved = ArgumentCaptor.forClass(Photo.class);
        verify(photos).save(saved.capture());
        assertThat(saved.getValue().getFilePath()).isEqualTo(key.getValue());
        assertThat(saved.getValue().getBag()).isSameAs(bag);
        assertThat(view.id()).isEqualTo(55L);
        assertThat(view.stage()).isEqualTo("KIA_FULL");
        assertThat(view.capturedBy()).isEqualTo("Test User");
        verify(staff).requireWriter();
    }

    @Test
    void storeMapsContentTypesToExtensions() {
        String[][] cases = {{"image/jpeg", ".jpg"}, {"image/jpg", ".jpg"}, {"IMAGE/PNG", ".png"}, {"image/webp", ".webp"}};
        for (String[] row : cases) {
            org.mockito.Mockito.clearInvocations(objects);
            service.store(1L, PhotoStage.EXCEPTION, file(row[0]));
            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(objects).put(key.capture(), any(), anyString());
            assertThat(key.getValue()).endsWith(row[1]);
        }
    }

    @Test
    void storeRejectsMissingOrEmptyFile() {
        api(() -> service.store(1L, PhotoStage.KIA_FULL, null), HttpStatus.BAD_REQUEST, "Photo is required");
        api(() -> service.store(1L, PhotoStage.KIA_FULL, new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[0])),
                HttpStatus.BAD_REQUEST, "Photo is required");
        verify(objects, never()).put(anyString(), any(), anyString());
    }

    @Test
    void storeRejectsUnsupportedContentTypes() {
        api(() -> service.store(1L, PhotoStage.KIA_FULL, file("image/gif")), HttpStatus.BAD_REQUEST, "JPEG, PNG, or WebP");
        api(() -> service.store(1L, PhotoStage.KIA_FULL, file(null)), HttpStatus.BAD_REQUEST, "JPEG, PNG, or WebP");
    }

    @Test
    void storeRejectsUnknownBag() {
        when(bags.findById(9L)).thenReturn(Optional.empty());
        api(() -> service.store(9L, PhotoStage.KIA_FULL, file("image/png")), HttpStatus.NOT_FOUND, "Bag was not found");
    }

    @Test
    void storeReportsIoFailures() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getContentType()).thenReturn("image/png");
        when(broken.getBytes()).thenThrow(new IOException("disk"));
        api(() -> service.store(1L, PhotoStage.KIA_FULL, broken), HttpStatus.INTERNAL_SERVER_ERROR, "could not be saved");
        verify(photos, never()).save(any());
    }

    @Test
    void storePropagatesWriterCheckFailure() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "read only")).when(staff).requireWriter();
        api(() -> service.store(1L, PhotoStage.KIA_FULL, file("image/png")), HttpStatus.FORBIDDEN, "read only");
    }

    @Test
    void kiaOfficerMayOnlyCaptureKiaAndNeutralStages() {
        as(Role.KIA_OFFICER);
        for (PhotoStage stage : new PhotoStage[]{PhotoStage.KIA_FULL, PhotoStage.KIA_TAG, PhotoStage.KIA_SEAL, PhotoStage.EXCEPTION}) {
            assertThat(service.store(1L, stage, file("image/png")).stage()).isEqualTo(stage.name());
        }
        for (PhotoStage stage : new PhotoStage[]{PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL, PhotoStage.RELEASE}) {
            api(() -> service.store(1L, stage, file("image/png")), HttpStatus.FORBIDDEN, "permission");
        }
    }

    @Test
    void znzOfficerMayOnlyCaptureZnzAndNeutralStages() {
        as(Role.ZNZ_OFFICER);
        for (PhotoStage stage : new PhotoStage[]{PhotoStage.ZNZ_CONDITION, PhotoStage.ZNZ_SEAL, PhotoStage.RELEASE, PhotoStage.EXCEPTION}) {
            assertThat(service.store(1L, stage, file("image/png")).stage()).isEqualTo(stage.name());
        }
        for (PhotoStage stage : new PhotoStage[]{PhotoStage.KIA_FULL, PhotoStage.KIA_TAG, PhotoStage.KIA_SEAL}) {
            api(() -> service.store(1L, stage, file("image/png")), HttpStatus.FORBIDDEN, "permission");
        }
    }

    @Test
    void adminMayCaptureEveryStage() {
        for (PhotoStage stage : PhotoStage.values()) {
            assertThat(service.store(1L, stage, file("image/webp")).stage()).isEqualTo(stage.name());
        }
    }

    // ---- load ----

    private Photo photo(String path) {
        Photo photo = new Photo();
        photo.setFilePath(path);
        when(photos.findById(5L)).thenReturn(Optional.of(photo));
        return photo;
    }

    @Test
    void loadReturnsBytesWithContentTypeFromExtension() {
        when(objects.get(anyString())).thenReturn(new byte[]{9});
        photo("photos/GL-1/a.PNG");
        assertThat(service.load(5L).contentType()).isEqualTo("image/png");
        photo("photos/GL-1/a.webp");
        assertThat(service.load(5L).contentType()).isEqualTo("image/webp");
        photo("photos/GL-1/a.jpg");
        PhotoService.StoredFile file = service.load(5L);
        assertThat(file.contentType()).isEqualTo("image/jpeg");
        assertThat(file.bytes()).containsExactly(9);
    }

    @Test
    void loadFailsForUnknownPhoto() {
        when(photos.findById(404L)).thenReturn(Optional.empty());
        api(() -> service.load(404L), HttpStatus.NOT_FOUND, "Photo was not found");
    }
}
