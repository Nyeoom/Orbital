# Orbital {{VERSION}}

Brimstone post-plant lineups for VALORANT. Orbital follows your match, finds where the spike was
planted and where you are standing, and shows matching lineups from
[lineupsvalorant.com](https://lineupsvalorant.com).

## Download

| File | What it is |
|---|---|
| **{{SETUP_NAME}}** | Installer (recommended). Double-click it, no Java needed. Installs for your user only and adds a Start menu entry. |
| **{{ZIP_NAME}}** | Portable. Unzip anywhere and run `Orbital\Orbital.exe`. |

Windows 10 / 11, 64-bit.

## First run

1. Run VALORANT in **borderless / windowed** mode (Orbital can't see an exclusive-fullscreen map).
2. Start Orbital. When you load into a match it picks up the map by itself, or pick one on the left.
3. After the spike is planted, open the full map (**M**) and press **F9**. The first time, drag a box
   around the map when asked (**Calibrate map region** redoes it).
4. Orbital shows lineups for the planted site, from your position. **Compact** gives you a small
   always-on-top window for playing next to the game.

## Heads up

- **The app is not code-signed**, so Windows SmartScreen will say *"Windows protected your PC"*.
  Click **More info**, then **Run anyway**. You can check the download against the SHA-256 below.
- Orbital only reads the game's log file and takes screenshots of your screen. It never touches
  the game process or its memory.
- Lineups and images come from lineupsvalorant.com through the same endpoints its own site uses.
  They are unofficial, so if the site changes them Orbital may need an update.
- This is the first public build. Spike detection reads colours off your screen and depends on your
  resolution and settings, so if F9 misses the spike, recalibrate the map region.

## Checksums (SHA-256)

```
{{SETUP_SHA}}  {{SETUP_NAME}}
{{ZIP_SHA}}  {{ZIP_NAME}}
```

Not affiliated with Riot Games or lineupsvalorant.com.
