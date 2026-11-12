# Matracraft plugins

Custom plugins for **Matracraft**, a private Minecraft server (Paper 26.2) run for a small group of friends.
Designed and directed by the repository owner; built with AI assistance (Claude). Released under the MIT license.

| Plugin | Version | What it does |
|---|---|---|
| **MATRAMOTORS** | 2.6.1 | Cars and boats with fuel priced from real petrol-station data, insurance, number plates, taximeter, jerrycans and a single vehicle menu (`/vehiculos`) |
| **Matrains** | 1.1.0 | Trains with custom rail physics: locomotives, freight and passenger cars, stations, automatic routes, an electric grid priced with the real Spanish electricity tariff, and lendable train keys |

## Real-world data
- **Fuel prices:** real petrol stations (Spanish Ministry open data) around a point you set in the config, refreshed every few hours. Outside Spain it uses a fixed default price.
- **Electricity price:** Spanish PVPC tariff (REE), used to bill electric trains.

## Repository layout
- `src/matracar`, `src/matrains` — Java source code.
- `res` — `plugin.yml` and default `config.yml` for each plugin.
- `tools` — build scripts (`build.sh`, `build-matrains.sh`).
- `LICENSE` — MIT.
- `releases` — compiled `.jar` files.
- `docs` — train system specification and design notes, plus the changelogs.

## Installing
Drop the jars from `releases/` into your Paper server's `plugins/` folder. Requirements, configuration and first steps: [`docs/INSTALL.md`](docs/INSTALL.md).

## Building
Requires Java 21+ and a `lib21/` folder with the Paper 26.2 API and the Vault, GriefPrevention, EssentialsX and Magic jars. These are not included, for size and licensing reasons.

```
bash tools/build.sh           # MATRAMOTORS
bash tools/build-matrains.sh  # Matrains
```

## Versioning
`MAJOR.MINOR.PATCH` — patch: fixes; minor: new features that do not break anything; major: big changes. Full history in `docs/CAMBIOS-*.md` (in Spanish).

## Notes
- Player-facing texts and in-game menus are in Spanish, since that is the server's language.
- This is a hobby project for a private server. It is not tied to or endorsed by Mojang or Microsoft.
