# Installing on your own server

## Requirements
- A **Paper** server (built against the 1.21+ API; tested on Paper 26.2) running **Java 21 or newer**.
- An economy: **Vault** (or VaultUnlocked) plus an economy plugin such as **EssentialsX**. Prices are charged through it.
- Optional: **GriefPrevention** (stations and claims), **Magic**, **Multiverse-Core**. MatraMotors starts fine without them.

## Steps
1. Download `MatraMotors-<version>.jar` from `releases/` and drop it in your server's `plugins/` folder. It contains cars, boats and trains.
2. Start the server once. The plugin creates `plugins/MatraMotors/` with `config.yml` (vehicles) and `config-trenes.yml` (trains). Stop the server.
3. Edit the configs (see below) and start again.
4. Give yourself admin: `coches.admin` and `matrains.admin` (operators have both by default).

## What you should change in `plugins/MatraMotors/config.yml` (cars and boats)
| Key | Why |
|---|---|
| `mundos-permitidos`, `mundos-prohibidos` | Worlds where vehicles may be driven. |
| `gasolina.latitud`, `gasolina.longitud`, `gasolina.provincia`, `gasolina.lugar` | Fuel price source. By default it averages real Spanish petrol stations near this point (Spanish Ministry open data). **Outside Spain it can't fetch prices**, so it falls back to `gasolina.precio-por-defecto-euros`. |
| `gasolina.euros-a-dolares` | Exchange rate between real euros and your in-game currency (shown as the symbol Ⓜ). |
| `taximetro.*` | Taxi tariffs, in euros. The defaults are an example, edit them. |
| `coches.*`, `barcos.*` | Every vehicle: price, speed, tank, consumption, seats. |
| `seguros.*` | Insurance rates. |

## What you should change in `plugins/MatraMotors/config-trenes.yml` (trains)
`mundos-permitidos`, the prices (`precios`, `locomotoras`, `vagones`), `limites`, and `combustible.euros-a-dolares`.
Electric trains are billed with the Spanish PVPC electricity price (REE); if it can't be read, `luz-precio-defecto` is used.

## First steps in game
- `/gasolinera crear [name]`: look at a 2x2 to 5x5 floor of one block type to make a petrol station. Under water it becomes a marine station.
- `/coche`, `/barco` or `/vehiculos`: buy and manage vehicles. `/gasolina`: today's price.
- `/tren`: train dealership, your trains and stations. Place locomotives on rails with right-click.
- Right-click a petrol pump on foot to buy a jerrycan.

## Language and currency
Player-facing texts are in **Spanish** and the currency symbol is `Ⓜ`. They are written directly in the Java source
(there are no language files yet). To translate or rename, edit the source and rebuild.

## Building from source
See the main README. You need Java 21+ and a `lib21/` folder with the Paper API jar and its dependencies, plus the
Vault, GriefPrevention, EssentialsX and Magic jars on the classpath (a Maven/Gradle setup would also work).
