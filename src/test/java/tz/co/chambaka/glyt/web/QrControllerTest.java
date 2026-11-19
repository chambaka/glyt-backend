package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.QrService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ScanRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrControllerTest {

    @Mock
    QrService qr;
    @Mock
    BagDetail detail;
    @InjectMocks
    QrController controller;

    @Test
    void scanDelegatesCode() {
        when(qr.scan("GL-1")).thenReturn(detail);
        assertThat(controller.scan(new ScanRequest("GL-1"))).isSameAs(detail);
    }

    @Test
    void scanPropagatesNotFound() {
        when(qr.scan("bad")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Bag was not found."));
        assertThatThrownBy(() -> controller.scan(new ScanRequest("bad"))).isInstanceOf(ApiException.class);
    }
}
