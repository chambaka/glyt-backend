# GoLyt Platform User Guide

A single, print-ready A4 guide that covers the whole GoLyt platform.

## Contents

| Part | Topic |
| ---- | ----- |
| A | Platform overview, roles, seeded users and end-to-end flow |
| B | Public website (traveller guide, 9 screenshots) |
| C | Ops console admin guide (every menu, SMS reset, password change, Users) |
| D | Daily operations tips (SLA queue, unpaid / pending bookings) |
| E | Officer phone app — KIA and Zanzibar custody (Flutter `glyt-app`) |

## Files

- `golyt-platform-user-guide.html` — source (self-contained styles, A4 `@page`)
- `GoLyt-Platform-User-Guide.pdf` — generated output
- `screenshots/ops/` — `guide-01` … `guide-15` ops console screenshots (+ `guide-01b`, `guide-01c`)
- `screenshots/public/` — `customer-01` … `customer-09` website screenshots
- `screenshots/app/` — `app-01` … `app-11` officer phone app screenshots
- `screenshots/logo.png` — cover logo

## Regenerate the PDF

Requires Google Chrome / Chromium.

```bash
cd docs/user-guide
google-chrome --headless --no-sandbox --disable-gpu --no-pdf-header-footer \
  --print-to-pdf=GoLyt-Platform-User-Guide.pdf \
  "file://$PWD/golyt-platform-user-guide.html"
```

Check the result:

```bash
ls -lh GoLyt-Platform-User-Guide.pdf
pdfinfo GoLyt-Platform-User-Guide.pdf   # optional, needs poppler-utils
```

## Editing tips

- Edit `golyt-platform-user-guide.html` directly; each chapter is a `<section class="page">`
  and prints on its own page. Part dividers use `<section class="part-divider">`.
- Image paths are relative (`screenshots/ops/...`, `screenshots/public/...`). Replace a PNG with the
  same file name to refresh a figure without editing the HTML.
- Use `.shot.narrow` / `.shot.medium` / `.shot.phone` to control screenshot width for tall or phone captures.

## Refreshing screenshots

- Ops console screenshots: see `glyt-admin-ui/docs/user-guide/` (`capture-screenshots.mjs`, `mock-api.mjs`).
- Website screenshots: capture from `glyt-website` (`npm run dev`) at about 1024 px wide, and save as
  `screenshots/public/customer-NN-*.png`.
- Officer app screenshots: run `flutter run -d chrome --web-port=5199 --dart-define=BACKEND_URL=http://127.0.0.1:8070`
  from `glyt-app`, sign in as `kia@golyt.co.tz` / `znz@golyt.co.tz`, capture phone-width views into
  `screenshots/app/app-NN-*.png`.

## Known screenshot note

`customer-05-book.png` shows a quote total of `TZS NaN`. Recapture it against a running backend with a
valid catalog price when convenient; the guide text does not depend on the amount.
