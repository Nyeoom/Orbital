# Orbital

Brimstone **post-plant lineup assistant** for VALORANT. Orbital follows your match, finds where the
spike was planted and where you are standing, and pulls matching Brimstone lineups from
[lineupsvalorant.com](https://lineupsvalorant.com).

> Not affiliated with Riot Games or lineupsvalorant.com. Orbital never touches the game process or
> its memory: it reads the game's log file and takes screenshots, nothing else. Lineups and images
> belong to their authors on lineupsvalorant.com.

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

Requires JDK 21+ and Maven. VALORANT must run in **borderless/windowed** mode so Orbital can
see the map.

```
run-app.bat                # build + launch (Windows)
mvn javafx:run             # same, via Maven
mvn test                   # unit tests (no network, no display needed)
```

First F9 asks you to drag a box around the map (**Calibrate map region** in the sidebar redoes it).
Resolution changes need a recalibration; one region is stored per screen resolution.

### Build a double-clickable app

```
package.bat                # -> dist\Orbital\Orbital.exe  (needs JDK 21 with jpackage; no WiX needed)
```

## Unknown maps

The log uses internal codenames (Breeze = `Foxtrot`, Fracture = `Canyon`, Haven = `Triad`, ...).
Only verified ones are built in (see `MapCodenames`). A new map shows a "which map is this?" bar
once and Orbital remembers your answer.

## Project layout

```
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
                   (java -cp ... com.appbrim.MockSite 8099, then run the app with
                    -Dorbital.site=http://127.0.0.1:8099 -Dorbital.cdn=http://127.0.0.1:8099)
```

The package name is still `com.appbrim` for historical reasons; renaming it is a pure IDE refactor.
