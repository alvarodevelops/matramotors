# Matracraft plugins

Custom plugins for **Matracraft**, a private Minecraft server (Paper 26.2) run for a small group of friends.
Designed and directed by the repository owner; built with AI assistance (Claude). Released under the MIT license.

**MatraMotors** (`0.1.0-beta`, early access) is a single plugin with three parts:

| Part | What it does |
|---|---|
| **Cars and boats** | Fuel priced from real petrol-station data, insurance, number plates, taximeter, jerrycans, car keys and a single vehicle menu (`/vehiculos`) |
| **Trains** | Custom rail physics: locomotives, freight and passenger cars, stations, automatic routes, an electric grid priced with the real Spanish electricity tariff, and lendable train keys |

## Real-world data
- **Fuel prices:** real petrol stations (Spanish Ministry open data) around a point you set in the config, refreshed every few hours. Outside Spain it uses a fixed default price.
- **Electricity price:** Spanish PVPC tariff (REE), used to bill electric trains.

## Repository layout
- `src/matracar` (cars and boats) and `src/matrains` (trains) — Java source code, built into one jar.
- `res/matracar` — `plugin.yml` and the default `config.yml` (vehicles) and `config-trenes.yml` (trains).
- `tools` — build script (`build.sh`).
- `LICENSE` — MIT.
- `releases` — compiled `.jar` files.
- `docs` — train system specification and design notes, plus the changelogs.

## Installing
Drop `MatraMotors-<version>.jar` from `releases/` into your Paper server's `plugins/` folder. Requirements, configuration and first steps: [`docs/INSTALL.md`](docs/INSTALL.md).

## Building
Requires Java 21+ and a `lib21/` folder with the Paper 26.2 API and the Vault, GriefPrevention, EssentialsX and Magic jars. These are not included, for size and licensing reasons.

```
bash tools/build.sh           # build/MatraMotors-<version>.jar
```

## Versioning
`MAJOR.MINOR.PATCH` — patch: fixes; minor: new features that do not break anything; major: big changes. Early-access versions are `0.x.y-beta`. Full history in `docs/CAMBIOS.md` (in Spanish).

## Notes
- Player-facing texts and in-game menus are in Spanish, since that is the server's language.
- This is a hobby project for a private server. It is not tied to or endorsed by Mojang or Microsoft.
