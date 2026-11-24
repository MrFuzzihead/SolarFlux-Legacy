# SolarFlux

[![](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![](https://img.shields.io/badge/Redstone%20Flux-CoFH-9b59b6)](https://github.com/CoFH/RedstoneFlux)

Solar panels for Minecraft 1.7.10 that produce Redstone Flux (RF), with upgrade slots and machine traversal. Inspired
by CompactSolars for IC2, but built around RF instead of EU.

Originally written by Nauktis; this fork keeps it building and running on the GTNH toolchain, fixes the long-standing
energy and inventory bugs, and adds a headless and in-game test harness.

## What it adds

| Kind | Contents |
|---|---|
| Blocks | 16 solar panels (tiers I–XVI); generation, transfer rate and capacity are per-tier config entries |
| Upgrades | blank, efficiency, low light, machine traversal, transfer rate, capacity, furnace |
| Crafting components | mirror, photovoltaic cells I–IV |

Every panel has upgrade slots in its GUI. The traversal upgrade pushes energy into nearby machines, the furnace upgrade
burns RF to smelt, and adjacent panels can share their energy when `BalanceEnergy` is enabled.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.7.10 |
| Minecraft Forge | 10.13.4.1614 |
| [CoFH Core / RedstoneFlux](https://github.com/CoFH/RedstoneFlux) | 1.7.10 (RF API) |
| Thermal Expansion | optional (adds its own recipes) |
| BuildCraft | optional (wrench support) |

## Building

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin:

```sh
./gradlew build              # compile, unit tests, format check, coverage gate
./gradlew test               # unit tests only
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew runClient          # dev client
./gradlew runServer          # dev server
```

### Tests

| Command | What it runs |
|---|---|
| `./gradlew test` | 292 headless JUnit 4 tests (JUnit 4.13.2 + Mockito 4.11.0, Java 8 launcher) |
| `./gradlew check` | the above plus Spotless, Checkstyle, the coverage floor and the game-test compile |
| `./gradlew gameTestServer` | boots a real dedicated server, runs 198 in-game assertions, then exits with the result. Also writes an in-game coverage report to `build/reports/jacoco/gameTest/html` |
| `./gradlew runClient -PgameTest` | boots a real client and runs 6 GUI/config assertions |

`check` also enforces a JaCoCo line/branch floor (55% line / 55% branch) plus per-package guards, so one large
uncovered class cannot hide behind the average. It is a regression guard, not a target; the uncovered remainder is
mostly the recipe registrars and the mod entry point, which need the Forge registries.

The in-game mod lives in `src/gameTest/java` and is never shipped. It covers what cannot be tested headless: the real
block and item registries, the crafting recipes, a panel placed and ticked in a real world, energy transfer into a
neighbouring receiver, an NBT round trip, and the GUI. Its coverage report is written to
`build/reports/jacoco/gameTest/html`; it is not merged into `jacocoTestReport`, because a Forge dev launch rewrites
every class through the `LaunchClassLoader`, so the class ids in `gameTest.exec` cannot match the class files on disk.

`gameTestServer` writes `run/server/eula.txt` and a deterministic flat `server.properties` before launching, so it needs
no manual setup. It prints `SOLARFLUX_GAMETEST_OK` on success and exits non-zero otherwise; the client run prints
`SOLARFLUX_CLIENTTEST_OK`. `.github/workflows/game-test.yml` checks both markers.

`CODE_REVIEW.md` records the dependency audit, the bug/optimization/refactor backlog (every P0–P3 item is closed,
with the deliberately-deferred ones and their reasons listed), the phased refactor plan and the test-coverage
ratchet.

### Configuration

`config/SolarFlux.cfg`, generated on first launch. The general section covers panel height, energy sharing, rain and
thunder production factors, connected textures and dismantle behaviour; `upgrades` has an enable flag and per-upgrade
limits for each upgrade; `solar_panel_tierN` sets `Active`, `MaximumEnergyGeneration`, `MaximumEnergyTransfer` and
`Capacity` for each tier.

Tier and registration changes require a restart: a panel stores its tier index in NBT, so disabling a tier only
removes its recipe and keeps the block (and any existing panel) intact.

## Development environment

The Gradle wrapper, the GTNH build script and the CI workflows are all in the repository; a plain `./gradlew build`
works from a fresh clone.

Java 25 runs Gradle and Java 8 runs the test suite (that is the mod's bytecode target and the newest JVM Mockito 4.x
supports). Both are downloaded by the wrapper's toolchain support if they are missing.

## Contributing

Bug reports and pull requests are welcome. Please read `CODE_REVIEW.md` before structural changes: it records the
known limitations and the ordered refactor plan.

If a bug looks like it is inside Forge or CoFH Core, please report it in the relevant repository instead.

## License

See [LICENSE.md](LICENSE.md).

## Credit

SolarFlux was created by Nauktis. Thanks to the GTNH team for the build tooling and to the CoFH team for the
RedstoneFlux API. This fork is maintained by MrFuzzihead.

---
