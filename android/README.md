# Little Navmap Mobile (Unofficial)

This directory contains an **unofficial Android adaptation** of
[Little Navmap](https://github.com/albar965/littlenavmap). It is not an
official Little Navmap release and is not endorsed by the upstream author.

Copyright (c) Alexander Barthel and the Little Navmap contributors.
This adaptation is free software distributed under the GNU General Public
License, version 3 or (at your option) any later version. See
[`../LICENSE.txt`](../LICENSE.txt) for the complete license text.
Legal and third-party notices are in [`NOTICE.md`](NOTICE.md). Instructions for
obtaining the exact corresponding source are in [`SOURCE.md`](SOURCE.md).

## Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools installed by the Android Gradle Plugin

The project uses Android Gradle Plugin 8.10.1, Gradle 8.11.1, Kotlin 2.1.21,
and Jetpack Compose. The minimum supported Android version is Android 9
(API 28).

## Build

From this directory, create `local.properties` with the Android SDK path (or
set `ANDROID_HOME`), then run:

```shell
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`.

The app connects to the web server built into a Little Navmap desktop
installation. Plain HTTP is allowed for local-network servers. HTTPS trusts
Android's system CAs and CAs that the device owner explicitly installed, while
certificate and hostname errors are never bypassed.

## Current scope

The app is a local-first flight-planning companion for a running Little Navmap
desktop instance. Its native mobile planner is the primary workflow: it supports
departure, destination, alternate, cruise level, SID, STAR, approach, and an
editable enroute sequence directly on the phone. The native vector route map is
drawn with Compose Canvas rather than a WebView or online tile provider, and
shows resolved route points, airspace grid and zoom controls without network map
latency. The Map workspace can switch between the mobile vector route and the
Little Navmap desktop map. The mobile view resolves typed departure, destination
and navaid identifiers from an installed mobile package or, when connected, the
Little Navmap `/api/airport/info` and `/api/map/features` APIs. The Little
Navmap view displays the route currently loaded by the desktop application.
Plans can be imported from Little Navmap `.lnmpln`, X-Plane 11/12 `.fms`
(v1100), the documented Little Navmap Mobile JSON format, or readable ICAO
route text. They can be exported as Little Navmap Mobile JSON, readable ICAO
route text, or an X-Plane 11/12 `.fms` (v1100) file when every route point has
validated coordinates. Weather uses the public Aviation Weather METAR service
when the device has Internet access. The app supports an English/Chinese
language switch from the upper-right corner. ICAO identifiers and aviation
terminology such as SID, STAR, AIRAC, METAR and X-Plane remain in English.

The connection workspace contains three provider panels. Little Navmap connects
to a local desktop Web API. SimBrief imports the newest released dispatch using
the user's public SimBrief username and does not ask for a password. Navigraph
imports a flight-plan export URL using a user-issued OAuth bearer token. The
token is held only in memory, is cleared after a successful import and is never
written to preferences. Navigraph OAuth applications require a Client ID and
redirect URI registered with Navigraph; this app deliberately does not embed a
shared client secret or collect Navigraph credentials.

Simulator live data, the map, airport information, and desktop navigation-data
resolution remain available through the Little Navmap Web API. Connect the
desktop instance that is itself connected to X-Plane 12, MSFS, or Prepar3D.
For a direct X-Plane 11/12 link, use the Simulator tab, enter the simulator's
LAN address and UDP port (normally 49000), and enable UDP DataRef access in
X-Plane. The direct link reads aircraft position, speed, heading, vertical
speed, and wind through the RREF protocol.
The Navigation data page accepts a portable Navmap Mobile navigation-data JSON
package and stores it locally. It does not claim to download a proprietary
AIRAC dataset. Keep the desktop Little Navmap database current as well when
using connected planning. A minimal test package is available at
[`dev/navigation-data-example.json`](dev/navigation-data-example.json).

The package has this shape:

```json
{
  "cycle": "2607",
  "airports": [{
    "identifier": "ZBAA",
    "latitude": 40.0801,
    "longitude": 116.5846,
    "elevationFeet": 116,
    "sids": ["DAXING 1A"],
    "stars": [],
    "approaches": []
  }],
  "fixes": [{
    "identifier": "GITUM",
    "latitude": 39.0,
    "longitude": 117.0,
    "altitudeFeet": 34000,
    "xPlaneType": 11
  }]
}
```

Airport and fix identifiers are resolved case-insensitively. Once the package
contains every point in a plan, the X-Plane FMS export is enabled and the SID,
STAR, and approach selector lists are populated from the relevant airport.

This release does not export native Little Navmap `.lnmpln` files. X-Plane FMS
export remains disabled for routes with unresolved coordinates, which prevents
the app from generating unsafe or misleading simulator routes.

The Aircraft page is native Compose UI backed by the stable `/api/sim/info`
endpoint and polls only while that page and the app are in the foreground. It
distinguishes an inactive simulator, live samples, stale data, HTTP/network
failures, and recovery.

The Airports page is also native Compose UI. It performs exact identifier
lookups through `/api/airport/info` and presents airport identifiers, runway
dimensions, weather reports, communications, facilities, parking, position,
and active-time information. The client handles the two COM-frequency integer
scales used by Little Navmap and treats the endpoint's runway dimensions as
feet, matching the desktop implementation. Map, Flight Plan, and Progress
continue to use Little Navmap's web frontend because upstream does not expose
stable structured Route or Progress APIs.

This is not yet a standalone offline port. A feature-equivalent offline build
requires separating the Qt/atools route and database core from the desktop
Widgets application, defining a JNI boundary, implementing a mobile map
renderer, and resolving navigation-database distribution and migration.

Run the local checks with:

```shell
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

The development fixture in [`dev/`](dev/) supplies active, inactive, not-found,
HTTP-error, and malformed simulator and airport responses for UI and recovery
testing.

For an installable, minified preview with a package name separate from the
future production app, run `gradlew.bat :app:assemblePreview`. The preview is
signed with the local Android debug key and must not be treated as a production
release. Production releases require a separately protected signing key.
