# Star Wars: Commander - Private Server (SwcNoops)

A reverse-engineered private server for the mobile strategy game **Star Wars: Commander** (closed by Disney/Zynga in 2020). 

This repository allows running the game locally or on a shared server, playing on real Android devices (or emulators) with fully working PvE campaign missions, PvP attacks against generated bases, base building, troop training, and custom modding.

---

## Current Status & Features

* **Platforms:** Runs on real Android devices (tested on modern devices like Samsung Galaxy S23 Ultra) and Android emulators.
* **Working Gameplay:** Login, 3D base rendering, base construction & upgrades, PvE campaign missions (217 generated missions), PvP attacks against HQ 5–11 layouts.
* **AutoRefill (`refill.json`):** Automatic replenishing of troops, starships, heroes, and Droideka champions after every battle and on login.
* **Base Fortification:** Built-in tools for defensive ring layouts (`tools/fortify_base.py`, `tools/base_layout.sh`).
* **Content Unlocks:** Halloween Heavy Undead Trooper (`EmpireHeavyStormDeath`) unlocked in barracks via reverse-engineered JOE patch data.
* **Asset Server:** Self-hosted CDN serving manifest `02045`, 1200+ asset bundles, UI strings, and audio.

---

## Quickstart Guide

### 1. Prerequisites

Make sure the following tools are installed:
* **Docker & Docker Desktop** (runs MongoDB 7 with replica set `rs0` for transaction support).
* **Java Development Kit (JDK 17 or 21)** (e.g. Eclipse Temurin).
* **Apache Maven** (`mvn`).
* **Python 3** (for tools and client metadata patcher).
* **Android SDK Build Tools** (`zipalign` and `apksigner`, only needed if patching client APKs).

### 2. Running the Server

1. **Start Docker Desktop**.
2. **Start the server**:
   ```bash
   ./run.sh
   ```
   * What `./run.sh` does automatically:
     - Starts a Docker container named `swc-mongo` with MongoDB 7 configured as a single-node replica set `rs0`.
     - Detects your machine's local Wi-Fi / Ethernet IP address.
     - Updates `cdnRoots` and `event2BiLoggingIpAddress` in `swc.properties`.
     - Compiles the Java project and starts the HTTP server on port `8080`.
3. **Verify the server is running**:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/swcFiles/manifests/__manifest_zyngaswc_prod.02045.json
   ```
   A response of `200` means the server is serving static assets and API requests.

### 3. Setting Up the Android Client

The game client needs to point to your server IP. The IP is baked into IL2CPP metadata.

1. Obtain a clean Star Wars Commander 7.8.1 APK (e.g. `star-wars-commander-7-8-1-253.apk`).
2. Run the automated patch script:
   ```bash
   ./tools/build_client.sh <YOUR_SERVER_IP>:8080 /path/to/star-wars-commander-7-8-1-253.apk
   ```
   *Example:* `./tools/build_client.sh 192.168.1.100:8080`
3. Install the generated APK on your device:
   ```bash
   adb install -r client/swc-patched-192.168.1.100_8080.apk
   ```
   *(All development APKs are signed with `client/swc-dev.keystore`, password: `swcserver`, alias: `swc`)*.

---

## Configuration

* **[swc.properties](file:///Users/karimbaylifarhad/claude_console/swc-server/swc.properties)**: Main server configuration. See `swc.properties.example` for detailed documentation of each field.
* **[refill.json](file:///Users/karimbaylifarhad/claude_console/swc-server/refill.json.example)**: Auto-refill army configuration. Copy `refill.json.example` to `refill.json` and insert your player UUID to keep your army replenished automatically.
* **Override IP:** If you want to force a specific IP or hostname without relying on auto-detection:
  ```bash
  SWC_HOST=192.168.1.50 ./run.sh
  ```

---

## Project Structure

```text
├── run.sh                  # Main startup script (Docker MongoDB + Java server)
├── pom.xml                 # Maven build definition (SparkJava, Jackson, MongoJack)
├── swc.properties          # Server properties (paths, CDN URLs, database)
├── swc.properties.example  # Reference configuration template
├── refill.json.example     # Template for auto-refill of player army
├── src/                    # Java source code
│   └── main/java/swcnoops/server/
│       ├── SparkMain.java  # HTTP server entry point & request logger
│       ├── Config.java     # Configuration manager
│       ├── session/        # Player, Guild, War sessions & AutoRefill
│       ├── commands/       # Game API command handlers
│       └── game/           # Game data, raids, and objective managers
├── swcFiles/               # Game asset bundles, manifest, strings, and battle maps
├── layouts/                # Validated PvP layouts for Empire and Rebel bases (HQ 5-11)
├── client/                 # Client signing keystores and client documentation
└── tools/                  # Developer utilities
    ├── build_client.sh     # One-step APK patcher and signer
    ├── patch_metadata.py   # IL2CPP global-metadata.dat address rewriter
    ├── gen_layouts.py      # Base layout generator and validator
    ├── fortify_base.py     # Procedural defensive base rebuilder
    ├── base_layout.sh      # Tool to apply base layouts directly into MongoDB
    └── joe/                # Zynga JOE binary asset serializer/deserializer
```

---

## Team Collaboration & Git Workflow

1. **Private Repository:** Because the project deals with Star Wars: Commander assets and client binaries, keep the remote repository **Private**.
2. **Branching Model:**
   * `main` is protected and should always remain stable.
   * Develop features and bugfixes in dedicated branches (`feature/<name>`, `fix/<name>`).
   * Submit changes via Pull Requests (PR) with review.
3. **Ignored Files:** APKs (`client/*.apk`), personal backups (`base_backups/`), and local run logs (`run.log`) are strictly ignored by `.gitignore`.
4. **Shared Dev Server (Recommended for Teams):**
   * For multiple developers to test multiplayer features (PvP against each other, squads, donations), deploy an instance of this server to an Ubuntu VPS with a static IP or domain name.
   * This eliminates the need to rebuild APKs when local Wi-Fi IPs change.

---

## Detailed Notes & Changelog

* **[README_CHANGES.md](file:///Users/karimbaylifarhad/claude_console/swc-server/README_CHANGES.md)**: Detailed breakdown of every bugfix and modification made in this fork.
* **[NOTES.md](file:///Users/karimbaylifarhad/claude_console/swc-server/NOTES.md)**: Dated engineering worklog, reverse-engineering notes, and test records.
