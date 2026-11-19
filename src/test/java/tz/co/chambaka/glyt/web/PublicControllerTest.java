package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.PublicTrackService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PickupView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PublicTrack;
import tz.co.chambaka.glyt.web.dto.GlytDtos.TimelineStep;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock
    PublicTrackService tracking;
    @InjectMocks
    PublicController controller;

    private static PublicTrack track(String code) {
        return new PublicTrack(
                code,
                "Ready for pickup",
                "2026-11-13 09:00",
                List.of(new TimelineStep("Received", true), new TimelineStep("Delivered", false)),
                new PickupView("Desk <1>", "ZNZ & Co", "06:00-22:00", "+255777000111", "255777000111")
        );
    }

    @Test
    void trackApiReturnsTrack() {
        PublicTrack track = track("GL-1");
        when(tracking.track("GL-1", "tok")).thenReturn(track);
        assertThat(controller.track("GL-1", "tok")).isSameAs(track);
    }

    @Test
    void trackApiPropagatesInvalidLink() {
        when(tracking.track("GL-1", "bad")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "invalid"));
        assertThatThrownBy(() -> controller.track("GL-1", "bad"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pageRendersHtmlWithEscapedValues() {
        when(tracking.track("GL-<1>", "tok")).thenReturn(track("GL-<1>"));
        String html = controller.page("GL-<1>", "tok");
        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("GL-&lt;1&gt;")
                .contains("Ready for pickup")
                .contains("Desk &lt;1&gt;")
                .contains("ZNZ &amp; Co")
                .contains("tel:+255777000111")
                .contains("<li class=\"done\"><span>✓</span>Received</li>")
                .contains("<li class=\"wait\"><span>○</span>Delivered</li>")
                .doesNotContain("GL-<1>");
    }

    @Test
    void pageHandlesNullFieldsAsEmpty() {
        PublicTrack track = new PublicTrack("GL-2", null, null, List.of(),
                new PickupView(null, null, null, null, null));
        when(tracking.track("GL-2", "tok")).thenReturn(track);
        assertThat(controller.page("GL-2", "tok")).contains("GL-2").contains("<ol></ol>");
    }

    @Test
    void pageRendersMissingForInvalidLink() {
        when(tracking.track("GL-1", "bad")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "invalid"));
        assertThat(controller.page("GL-1", "bad")).contains("Link not valid");
    }
}
