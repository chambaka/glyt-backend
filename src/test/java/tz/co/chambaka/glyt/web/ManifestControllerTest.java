package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tz.co.chambaka.glyt.service.ManifestPdfService;
import tz.co.chambaka.glyt.service.ManifestService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.AddBagsRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CreateManifestRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ManifestView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManifestControllerTest {

    @Mock
    ManifestService manifests;
    @Mock
    ManifestPdfService pdf;
    @Mock
    ManifestView view;
    @InjectMocks
    ManifestController controller;

    @Test
    void listDelegates() {
        List<ManifestView> rows = List.of(view);
        when(manifests.list()).thenReturn(rows);
        assertThat(controller.list()).isSameAs(rows);
    }

    @Test
    void createDelegates() {
        CreateManifestRequest request = mock(CreateManifestRequest.class);
        when(manifests.create(request)).thenReturn(view);
        assertThat(controller.create(request)).isSameAs(view);
    }

    @Test
    void getDelegates() {
        when(manifests.get(1L)).thenReturn(view);
        assertThat(controller.get(1L)).isSameAs(view);
    }

    @Test
    void getPropagatesNotFound() {
        when(manifests.get(1L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "missing"));
        assertThatThrownBy(() -> controller.get(1L)).isInstanceOf(ApiException.class);
    }

    @Test
    void pdfSetsHeaders() {
        byte[] body = {'%', 'P', 'D', 'F'};
        when(pdf.pdf(4L)).thenReturn(body);
        ResponseEntity<byte[]> response = controller.pdf(4L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=manifest-4.pdf");
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void addBagsDelegates() {
        when(manifests.addBags(1L, List.of(2L, 3L))).thenReturn(view);
        assertThat(controller.add(1L, new AddBagsRequest(List.of(2L, 3L)))).isSameAs(view);
    }

    @Test
    void removeBagDelegates() {
        when(manifests.removeBag(1L, 2L)).thenReturn(view);
        assertThat(controller.remove(1L, 2L)).isSameAs(view);
    }

    @Test
    void finaliseDelegates() {
        when(manifests.finalise(1L)).thenReturn(view);
        assertThat(controller.finalise(1L)).isSameAs(view);
    }

    @Test
    void handoverDelegates() {
        when(manifests.handover(1L)).thenReturn(view);
        assertThat(controller.handover(1L)).isSameAs(view);
    }

    @Test
    void missingDelegates() {
        when(manifests.flagMissing(1L)).thenReturn(view);
        assertThat(controller.missing(1L)).isSameAs(view);
    }
}
