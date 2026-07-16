# Little Navmap Android development mock

`mock-server.ps1` is a dependency-free, development-only HTTP server for
testing the Android client when a desktop Little Navmap instance is not
available. It listens on every network interface. Do not expose it to an
untrusted network or use it as a production server.

By default it serves a small, responsive fixture with mock data:

Run it from PowerShell without changing the machine-wide execution policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1
```

The default address is `http://0.0.0.0:8965/`. To use a different port:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -Port 9000
```

The mock serves `/api/sim/info` using the same JSON field names as Little
Navmap. Its default `Active` fixture represents a connected aircraft. Use
`-SimState` to exercise the other client states:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -SimState Inactive
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -SimState HttpError
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -SimState Malformed
```

`Inactive` returns Little Navmap's real disconnected shape,
`{"active":false}`. `HttpError` returns HTTP 503, and `Malformed` returns an
intentionally incomplete JSON document with HTTP 200. Add `-Verbose` to log
each request while testing polling behavior.

The mock also serves `/api/airport/info`. Like Little Navmap, this endpoint
performs an exact airport-ident lookup rather than a name or prefix search. The
default `Found` fixture returns a complete Portland International (`KPDX`)
response for `ident=KPDX`, matched without regard to case. Missing idents and
all other values return HTTP 404 with `Airport not found`.

Use `-AirportState` to exercise the native airport screen's other response
paths:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -AirportState NotFound
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -AirportState HttpError
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\mock-server.ps1 -AirportState Malformed
```

`NotFound` forces the known `KPDX` lookup to return HTTP 404, `HttpError`
returns HTTP 503, and `Malformed` returns an intentionally incomplete JSON
document with HTTP 200. Unknown or missing idents return 404 in every state.
The full fixture includes airport metadata, runway dimensions in feet, METAR
sources, facilities, parking capacities, and both legacy and X-Plane 8.33 kHz
COM integer scales.

To exercise the Android WebView against the repository's real web frontend,
pass the repository `web` directory as `-WebRoot`. From the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\android\dev\mock-server.ps1 -WebRoot .\web
```

This source-web mode serves the files under `web` without modifying them. It
also provides the mock `/api/ui/info`, `/api/sim/info`, and
`/api/airport/info` responses required by the Android client, plus the
`/plugins` directory list used by the frontend. API routes are handled before
static files, so `-SimState` and `-AirportState` work the same with or without
`-WebRoot`. Little Navmap's other dynamic endpoints, including `/mapimage` and
`/refresh`, still require a running desktop Little Navmap instance. The
source-web mode is intended for frontend layout, WebView integration,
aircraft-state tests, and airport-state tests, not full flight-data
simulation.

Connect an emulator to `10.0.2.2` or an Android device to the computer's LAN
address. Press Ctrl+C in PowerShell to stop the server.

Copyright 2015-2026 Alexander Barthel and the Little Navmap contributors.
SPDX-License-Identifier: GPL-3.0-or-later

Modified for the unofficial Little Navmap Android client in 2026. This mock
server is not part of an official Little Navmap release.
