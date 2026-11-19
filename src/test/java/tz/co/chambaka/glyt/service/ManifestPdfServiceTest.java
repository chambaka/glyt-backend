package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.domain.ManifestBag;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.OpsEnums.ManifestStatus;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManifestPdfServiceTest {

    @Mock ManifestRepository manifests;
    @Mock ManifestBagRepository links;

    private ManifestPdfService service;
    private Manifest manifest;

    @BeforeEach
    void setUp() {
        service = new ManifestPdfService(manifests, links);
        manifest = new Manifest();
        manifest.setId(1L);
        manifest.setNumber("MF-JRO-ZNZ-00001");
        manifest.setManifestDate("2026-01-10");
        manifest.setRoute("JRO-ZNZ");
        manifest.setAirline("Precision Air");
        manifest.setFlightNumber("PW 100");
        manifest.setStatus(ManifestStatus.FINALISED);
        manifest.setPreparedBy(TestFixtures.staff(Role.KIA_OFFICER, "KIA"));
    }

    private static ManifestBag link(Bag bag) {
        ManifestBag link = new ManifestBag();
        link.setBag(bag);
        return link;
    }

    private static void assertPdf(byte[] bytes) {
        assertThat(bytes).hasSizeGreaterThan(200);
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String tail = new String(bytes, Math.max(0, bytes.length - 16), Math.min(16, bytes.length), StandardCharsets.ISO_8859_1);
        assertThat(tail).contains("%%EOF");
    }

    @Test
    void pdfWithBagsHasPdfMagicBytes() {
        Bag sealed = ServiceTestData.bag(1L, BagStatus.SEALED);
        sealed.setSealNumber("SEAL-1");
        Bag unsealed = ServiceTestData.bag(2L, BagStatus.RECEIVED_KIA);
        unsealed.setSealNumber(null);
        when(manifests.findById(1L)).thenReturn(Optional.of(manifest));
        when(links.findByManifestId(1L)).thenReturn(List.of(link(sealed), link(unsealed)));

        assertPdf(service.pdf(1L));
    }

    @Test
    void pdfForEmptyManifestIsStillValid() {
        when(manifests.findById(1L)).thenReturn(Optional.of(manifest));
        when(links.findByManifestId(1L)).thenReturn(List.of());
        assertPdf(service.pdf(1L));
    }

    @Test
    void pdfFailsForUnknownManifest() {
        when(manifests.findById(9L)).thenReturn(Optional.empty());
        ApiException ex = ServiceTestData.expectApi(() -> service.pdf(9L));
        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("Manifest was not found");
    }

    @Test
    void renderingFailureIsReportedAsServerError() {
        manifest.setPreparedBy(null); // dereferenced while building the header
        when(manifests.findById(1L)).thenReturn(Optional.of(manifest));
        when(links.findByManifestId(1L)).thenReturn(List.of());
        ApiException ex = ServiceTestData.expectApi(() -> service.pdf(1L));
        assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).contains("could not be printed");
    }
}
