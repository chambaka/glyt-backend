package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.PhotoStage;
import tz.co.chambaka.glyt.service.BagQueryService;
import tz.co.chambaka.glyt.service.BagService;
import tz.co.chambaka.glyt.service.PhotoService;
import tz.co.chambaka.glyt.service.QrService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BagControllerTest {

    @Mock
    BagService bags;
    @Mock
    BagQueryService queries;
    @Mock
    PhotoService photos;
    @Mock
    QrService qr;
    @Mock
    BagDetail detail;
    @InjectMocks
    BagController controller;

    @Test
    void searchDelegatesQuery() {
        List<BagSummary> rows = List.of(mock(BagSummary.class));
        when(queries.search("GL-1")).thenReturn(rows);
        assertThat(controller.search("GL-1")).isSameAs(rows);
    }

    @Test
    void searchAllowsNullQuery() {
        when(queries.search(null)).thenReturn(List.of());
        assertThat(controller.search(null)).isEmpty();
    }

    @Test
    void createDelegates() {
        CreateBagRequest request = mock(CreateBagRequest.class);
        when(bags.create(request)).thenReturn(detail);
        assertThat(controller.create(request)).isSameAs(detail);
    }

    @Test
    void getReturnsDetail() {
        when(queries.detail(5L)).thenReturn(detail);
        assertThat(controller.get(5L)).isSameAs(detail);
    }

    @Test
    void getPropagatesNotFound() {
        when(queries.detail(9L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        assertThatThrownBy(() -> controller.get(9L))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void qrReturnsPngBody() {
        byte[] png = {1, 2, 3};
        when(qr.png(5L)).thenReturn(png);
        ResponseEntity<byte[]> response = controller.qr(5L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(png);
    }

    @Test
    void byCodeDelegates() {
        when(queries.byCode("GL-1")).thenReturn(detail);
        assertThat(controller.byCode("GL-1")).isSameAs(detail);
    }

    @Test
    void receiveDelegates() {
        ReceiveRequest request = mock(ReceiveRequest.class);
        when(bags.receive(1L, request)).thenReturn(detail);
        assertThat(controller.receive(1L, request)).isSameAs(detail);
    }

    @Test
    void sealDelegates() {
        SealRequest request = mock(SealRequest.class);
        when(bags.seal(1L, request)).thenReturn(detail);
        assertThat(controller.seal(1L, request)).isSameAs(detail);
    }

    @Test
    void receiveZnzDelegates() {
        ZnzReceiveRequest request = mock(ZnzReceiveRequest.class);
        when(bags.receiveZnz(1L, request)).thenReturn(detail);
        assertThat(controller.receiveZnz(1L, request)).isSameAs(detail);
    }

    @Test
    void storePassesStorageCode() {
        when(bags.store(1L, "A-01")).thenReturn(detail);
        assertThat(controller.store(1L, new StoreRequest("A-01"))).isSameAs(detail);
    }

    @Test
    void readyDelegates() {
        when(bags.ready(1L)).thenReturn(detail);
        assertThat(controller.ready(1L)).isSameAs(detail);
    }

    @Test
    void releaseDelegates() {
        ReleaseRequest request = mock(ReleaseRequest.class);
        when(bags.release(1L, request)).thenReturn(detail);
        assertThat(controller.release(1L, request)).isSameAs(detail);
    }

    @Test
    void cancelDelegates() {
        when(bags.cancel(1L)).thenReturn(detail);
        assertThat(controller.cancel(1L)).isSameAs(detail);
    }

    @Test
    void correctDelegates() {
        CorrectRequest request = mock(CorrectRequest.class);
        when(bags.correct(1L, request)).thenReturn(detail);
        assertThat(controller.correct(1L, request)).isSameAs(detail);
    }

    @Test
    void photoParsesStageAndStores() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        PhotoView view = mock(PhotoView.class);
        when(photos.store(1L, PhotoStage.KIA_FULL, file)).thenReturn(view);
        assertThat(controller.photo(1L, "kia-full", file)).isSameAs(view);
    }

    @Test
    void photoRejectsUnknownStage() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> controller.photo(1L, "nope", file))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(photos);
    }

    @Test
    void photoRejectsBlankStage() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> controller.photo(1L, " ", file)).isInstanceOf(ApiException.class);
    }
}
