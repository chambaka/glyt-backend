package tz.co.chambaka.glyt.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tz.co.chambaka.glyt.service.PublicTrackService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.PublicTrack;
import tz.co.chambaka.glyt.web.dto.GlytDtos.TimelineStep;

@RestController
public class PublicController {

    private final PublicTrackService tracking;

    public PublicController(PublicTrackService tracking) {
        this.tracking = tracking;
    }

    @GetMapping("/api/v1/public/track/{code}/{token}")
    public PublicTrack track(@PathVariable String code, @PathVariable String token) {
        return tracking.track(code, token);
    }

    @GetMapping(value = "/track/{code}/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String code, @PathVariable String token) {
        try {
            return render(tracking.track(code, token));
        } catch (ApiException ex) {
            return renderMissing();
        }
    }

    private String render(PublicTrack track) {
        StringBuilder steps = new StringBuilder();
        for (TimelineStep step : track.timeline()) {
            steps.append("<li class=\"")
                    .append(step.done() ? "done" : "wait")
                    .append("\"><span>")
                    .append(step.done() ? "✓" : "○")
                    .append("</span>")
                    .append(escape(step.label()))
                    .append("</li>");
        }
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>GoLyt · %s</title>
                  <style>
                    :root { --navy:#071C37; --blue:#638DB5; --bg:#F7FAFC; --ink:#334155; --line:#E2E8F0; --ok:#16A34A; }
                    * { box-sizing: border-box; }
                    body { margin:0; font-family: "Segoe UI", sans-serif; background:var(--bg); color:var(--ink); }
                    header { background:var(--navy); color:white; padding:28px 20px 32px; text-align:center; }
                    header img { height:54px; background:white; border-radius:10px; padding:6px 10px; }
                    main { max-width:520px; margin:-18px auto 40px; padding:0 16px; }
                    .card { background:white; border:1px solid var(--line); border-radius:16px; padding:20px; margin-bottom:14px; }
                    h1 { margin:0 0 6px; color:var(--navy); font-size:28px; letter-spacing:.04em; }
                    .status { color:var(--blue); font-size:18px; font-weight:650; }
                    .muted { color:#64748b; font-size:13px; }
                    ol { list-style:none; margin:0; padding:0; }
                    li { display:flex; gap:10px; padding:8px 0; border-bottom:1px solid var(--line); }
                    li span { width:22px; color:#94a3b8; font-weight:700; }
                    li.done span { color:var(--ok); }
                    a { color:var(--blue); }
                  </style>
                </head>
                <body>
                  <header>
                    <img src="/brand/logo.jpg" alt="GoLyt"/>
                    <p>Luggage storage &amp; transfer</p>
                  </header>
                  <main>
                    <section class="card">
                      <h1>%s</h1>
                      <p class="status">%s</p>
                      <p class="muted">Last updated %s</p>
                    </section>
                    <section class="card">
                      <ol>%s</ol>
                    </section>
                    <section class="card">
                      <strong style="color:var(--navy)">Zanzibar pickup</strong>
                      <p>%s<br/>%s<br/>%s</p>
                      <p><a href="tel:%s">%s</a></p>
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(
                escape(track.bagCode()),
                escape(track.bagCode()),
                escape(track.status()),
                escape(track.lastUpdated()),
                steps,
                escape(track.pickup().name()),
                escape(track.pickup().place()),
                escape(track.pickup().hours()),
                escape(track.pickup().phone()),
                escape(track.pickup().phone())
        );
    }

    private String renderMissing() {
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
                <title>GoLyt</title>
                <style>body{font-family:sans-serif;background:#F7FAFC;color:#334155;display:grid;place-items:center;min-height:100vh;margin:0}
                .card{background:white;border:1px solid #E2E8F0;border-radius:16px;padding:28px;max-width:420px} h1{color:#071C37}</style>
                </head><body><div class="card"><h1>Link not valid</h1><p>This tracking link is not valid. Ask GoLyt to send it again.</p></div></body></html>
                """;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
