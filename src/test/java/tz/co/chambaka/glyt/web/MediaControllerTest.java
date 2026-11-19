package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tz.co.chambaka.glyt.service.PhotoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    PhotoService photos;
    @InjectMocks
    MediaController controller;

    @Test
    void returnsStoredBytesWithContentType() {
        byte[] bytes = {9, 8, 7};
        when(photos.load(1L)).thenReturn(new PhotoService.StoredFile(bytes, "image/png"));
        ResponseEntity<byte[]> response = controller.get(1L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(bytes);
    }

    @Test
    void propagatesNotFound() {
        when(photos.load(2L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Photo was not found."));
        assertThatThrownBy(() -> controller.get(2L))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
