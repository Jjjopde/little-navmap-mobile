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
desktop instance. Its native planner supports departure, destination,
alternate, cruise level, SID, STAR, approach, and an editable enroute sequence.
Plans can be imported from Little Navmap `.lnmpln`, X-Plane 11/12 `.fms`
(v1100), the documented Little Navmap Mobile JSON format, or readable ICAO
route text. They can be exported as Little Navmap Mobile JSON or readable ICAO
route text. Weather uses the public Aviation Weather METAR service when the
device has Internet access.

Simulator live data, the map, airport information, and desktop navigation-data
resolution remain available through the Little Navmap Web API. Connect the
desktop instance that is itself connected to X-Plane 12, MSFS, or Prepar3D.
The Navigation data page deliberately directs updates to the desktop database:
the mobile app does not claim to install a proprietary AIRAC dataset locally.

This release does not export X-Plane `.fms` or native Little Navmap `.lnmpln`
files. Both formats need validated waypoint coordinates and procedure data;
generating them without a licensed, current navigation database would create
unsafe or misleading routes.

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
