![Spinning cube](https://github.com/user-attachments/assets/154e3ff5-62de-4abb-958a-9411488165fc)

<p align="center"><i>A cube should be spinning.</i></p>

<br>

<p align="center">
  <img src="https://i.imgur.com/xTiavJZ.png" alt="Title" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Current%20Version-0.1.0-orange" alt="Current Version 0.1.0" />
  <a href="https://github.com/Niravanaa/yart/actions/workflows/ci-main.yml">
    <img src="https://github.com/Niravanaa/yart/actions/workflows/ci-main.yml/badge.svg?branch=main" alt="Build Status" />
  </a>
  <img src="https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/Niravanaa/6e70009da55347696b1d22d1b35225d3/raw/coverage.json&cacheSeconds=300&v=1" alt="Coverage" />
  <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Paper-1.21.5-00AEEF" alt="Paper 1.21.5" />
</p>

<p align="center">
A Paper plugin that raytraces OBJ/JSON scenes into a live in-game screen made of BlockDisplay entities.
</p>

## Quick Start

1. Build the plugin jar:

```bash
gradle build
```

2. Copy the generated jar from `build/libs/` into your Paper server `plugins/` folder.

3. Start the server once to create plugin data folders.

4. Put model files here:

`plugins/YetAnotherRayTracer/models/`

5. In-game (as op):

```text
/raytrace load your_model.obj
/raytrace load your_scene.json
/raytrace start 32 18
```

Stop/reset:

```text
/raytrace stop
/raytrace reset
```

## Custom Block Palettes (Blockset Configuration)

By default, the plugin uses a bundled 16-block fallback palette. A custom blockset can be generated for wider color choice:

### Automatic Blockset Generation

The plugin includes a PowerShell script to generate blocksets from the official Mojang client JAR:

```powershell
# From project root, run:
.\scripts\generate-blockset-from-mojang.ps1 -Version "26.1"

# Output: plugins/YetAnotherRayTracer/blockset.json
```

**Requirements:**

- PowerShell 5.1+
- .NET Framework 4.7+
- Internet connection (downloads Mojang client JAR ~150MB)

**What This Does:**

1. Downloads the official Minecraft client JAR from Mojang Piston Meta API
2. Extracts block textures from the JAR
3. Computes average RGB color per block material
4. Filters out non-solid/non-occluding blocks (glass, saplings, etc.)
5. Generates `blockset.json` with 500+ block entries

### Blockset Format

The generated `blockset.json` follows this schema:

```json
{
  "name": "mojang-26.1-generated",
  "colorSpace": "srgb",
  "blocks": [
    {"material": "STONE", "rgb": [0.5, 0.5, 0.5]},
    {"material": "OAK_LOG", "rgb": [0.4, 0.25, 0.1]},
    ...
  ]
}
```

**Location:** `plugins/YetAnotherRayTracer/blockset.json`

**Fallback:** If blockset.json is missing, the plugin automatically uses the bundled 16-block default palette.
