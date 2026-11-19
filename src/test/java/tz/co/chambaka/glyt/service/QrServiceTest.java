package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.repo.BagRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.BagDetail;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock BagRepository bags;
    @Mock BagQueryService queries;
    @InjectMocks QrService service;

    @Test
    void pngReturnsAValidPngImage() {
        when(bags.findById(1L)).thenReturn(Optional.of(ServiceTestData.bag(1L, BagStatus.BOOKED)));

        byte[] png = service.png(1L);

        assertThat(png).hasSizeGreaterThan(100);
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");
    }

    @Test
    void pngFailsForUnknownBag() {
        when(bags.findById(2L)).thenReturn(Optional.empty());
        ApiException ex = ServiceTestData.expectApi(() -> service.png(2L));
        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pngReportsEncodingFailureAsServerError() {
        Bag bag = ServiceTestData.bag(1L, BagStatus.BOOKED);
        bag.setCode("X".repeat(5000)); // larger than the biggest QR symbol
        when(bags.findById(1L)).thenReturn(Optional.of(bag));
        ApiException ex = ServiceTestData.expectApi(() -> service.png(1L));
        assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).contains("QR code could not be created");
    }

    @Test
    void scanRejectsBlankInput() {
        for (String raw : new String[]{null, "", "   "}) {
            ApiException ex = ServiceTestData.expectApi(() -> service.scan(raw));
            assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void scanNormalisesAllSupportedFormats() {
        BagDetail detail = mock(BagDetail.class);
        when(queries.byCode("GL-1")).thenReturn(detail);

        assertThat(service.scan("GL-1")).isSameAs(detail);
        assertThat(service.scan("  GL-1  ")).isSameAs(detail);
        assertThat(service.scan("golyt:GL-1")).isSameAs(detail);
        assertThat(service.scan("GOLYT:GL-1")).isSameAs(detail);
        assertThat(service.scan("https://golyt.co.tz/track/GL-1")).isSameAs(detail);
        verify(queries, org.mockito.Mockito.times(5)).byCode("GL-1");
    }

    @Test
    void scanKeepsHttpPrefixedCodeWithoutSlash() {
        BagDetail detail = mock(BagDetail.class);
        when(queries.byCode("httpcode")).thenReturn(detail);
        assertThat(service.scan("httpcode")).isSameAs(detail);
    }
}
