# Orbital

Brimstone **post-plant lineup assistant** for VALORANT. Orbital follows your match, finds where the
spike was planted and where you are standing, and pulls matching Brimstone lineups from
[lineupsvalorant.com](https://lineupsvalorant.com).

> Not affiliated with Riot Games or lineupsvalorant.com. Orbital never touches the game process or
> its memory: it reads the game's log file and takes screenshots, nothing else. Lineups and images
> belong to their authors on lineupsvalorant.com.

## Download

Grab the latest build from the [**Releases**](../../releases/latest) page. No Java needed:

- `Orbital-Setup-<version>.exe`: installer (recommended)
- `Orbital-<version>-windows-portable.zip`: unzip and run `Orbital\Orbital.exe`

Windows will warn that the app is unsigned (*SmartScreen*): **More info -> Run anyway**. Releases
are built by GitHub Actions from this repo (see `.github/workflows/release.yml`), and the release
notes list SHA-256 checksums. `Orbital.exe --selftest report.txt` runs diagnostics (JavaFX, WebP,
HTTPS, a live search against the site) if something doesn't work on your PC.

## How it works

| Step | What happens |
|---|---|
| Map + phase | Tails `%LOCALAPPDATA%\Valorant\Saved\Logs\ShooterGame.log`. Works if you start Orbital mid-match. |
| Spike + you | Press **F9** with the full map (**M**) open. Orbital screenshots the calibrated map region, finds the red spike marker and the white arrow that is you. |
| Callouts | Marker positions are sent to the site's `get_callout` endpoint -> "A Site", "A Main", ... |
| Lineups | `get_results` is searched for that map/site, preferring lineups thrown from your callout. |

## Features

- Auto-detected map, spike site and your position (**FROM ME** toggle to ignore position)
- Molly / Smokes / Both, Attack / Defense, manual site override
- Lineup detail view: walkthrough images (arrow keys, click, `Esc` to go back), numbered steps, copy link
- **Saved** lineups (star them on a card or in the detail view; kept offline in your prefs)
- **Compact mode**: small always-on-top window with adjustable opacity for use next to the game
- Remembers window size/position, filters and calibration; caches images on disk (auto-pruned)

## Running

The project lives in the `Orbital/` folder of this repo; run the commands below from inside it
(`cd Orbital`). Requires JDK 21+ and Maven. VALORANT must run in **borderless/windowed** mode so Orbital can
see the map.

```
run-app.bat                # build + launch (Windows)
mvn javafx:run             # same, via Maven
mvn test                   # unit tests (no network, no display needed)
```

First F9 asks you to drag a box around the map (**Calibrate map region** in the sidebar redoes it).
Resolution changes need a recalibration; one region is stored per screen resolution.

### Build the Windows app yourself

```
package.bat                 # -> dist\Orbital\Orbital.exe (portable folder)
package.bat 1.2.0 installer # also dist\Orbital-Setup-1.2.0.exe (needs WiX Toolset 3.x on PATH)
```

Needs JDK 21 (includes `jpackage`) and Maven. The script keeps an explicit module list for the
bundled Java runtime on purpose: `jpackage`'s own detection leaves out `jdk.crypto.ec`, which makes
every HTTPS request to the lineup site fail the TLS handshake.

### Publishing a release

Push a version tag; GitHub Actions builds on Windows, runs the tests and the packaged app's
self-test, and attaches the installer and zip to a new release:

```
git tag v1.0.0
git push origin v1.0.0
```

## Unknown maps

The log uses internal codenames (Breeze = `Foxtrot`, Fracture = `Canyon`, Haven = `Triad`, ...).
Only verified ones are built in (see `MapCodenames`). A new map shows a "which map is this?" bar
once and Orbital remembers your answer.

## Project layout

```
Orbital/                   Maven project (pom.xml, run-app.bat, package.bat, packaging/)
src/main/java/com/appbrim
  App, Launcher, Prefs
  api/     LineupsClient (site JSON API, caching, retry), ImageCache
  detect/  LogWatcher, ScreenAnalyzer, MapCodenames, Callouts
  model/   GameState, Lineup, LineupDetail, Favorites, StepText
  hotkey/  GlobalHotkey (F9 via JNA)
  ui/      MainWindow, LineupCard, DetailView, CalibrationDialog, ClusterPickerDialog
src/test/java/com/appbrim
  *Test            JUnit 5 tests
  MockSite         offline stand-in for lineupsvalorant.com
  FakeMap          fake in-game map window (red spike + white player marker)
```

## Trying it without a match

`MockSite` and `FakeMap` (both in `src/test`) let you exercise the whole F9 flow offline:

```
mvn -q test-compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
set /p CP=<target\cp.txt
start java -cp "target\classes;target\test-classes;%CP%" com.appbrim.MockSite 8099
start java -cp "target\classes;target\test-classes;%CP%" com.appbrim.FakeMap
java -Dorbital.site=http://127.0.0.1:8099 -Dorbital.cdn=http://127.0.0.1:8099 -cp "target\classes;%CP%" com.appbrim.Launcher
```

Pick Breeze on the left, calibrate a box around the fake map window, press F9: the spike resolves to
"A Site" and you to "A Main", and the grid shows the mock lineups from your position.

The package name is still `com.appbrim` for historical reasons; renaming it is a pure IDE refactor.
