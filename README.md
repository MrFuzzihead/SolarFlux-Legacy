SolarFlux
=========

Mod for Minecraft - Adds solar panels that produce Redstone Flux

Inspired from CompactSolars for IC2 but works with Redstone Flux instead :)

## Building ##

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin:

```
./gradlew build                # compile, unit tests, format check, coverage gate
./gradlew test                 # unit tests only
./gradlew jacocoTestReport     # HTML coverage -> build/reports/jacoco/test/html
./gradlew runClient            # dev client
./gradlew runServer            # dev server
```

### Tests ###

| Command | What it runs |
| --- | --- |
| `./gradlew test` | headless JUnit 4 tests (JUnit 4.13.2 + Mockito 4.11.0, Java 8 launcher) |
| `./gradlew check` | the above plus Spotless, Checkstyle, the coverage floor and the game-test compile |
| `./gradlew gameTestServer` | boots a real dedicated server, runs the in-game assertions, then exits with the result |

`check` also enforces a JaCoCo line/branch floor (48.6% line / 52.7% branch at the time the harness landed). It is a
regression guard, not a target; the uncovered remainder is the recipe registrars, the item and block registration and
the mod entry point, all of which need the Forge registries.

The in-game mod lives in `src/gameTest/java` and is never shipped. It covers what cannot be tested headless: the real
block and item registries, the crafting recipes, a panel placed and ticked in a real world, energy transfer into a
neighbouring receiver, and an NBT round trip. Its coverage report is written to `build/reports/jacoco/gameTest/html`;
it is not merged into `jacocoTestReport`, because a Forge dev launch rewrites every class through the
`LaunchClassLoader`, so the class ids in `gameTest.exec` cannot match the class files on disk.

`gameTestServer` writes `run/server/eula.txt` and a deterministic flat `server.properties` before launching, so it needs
no manual setup. It prints `SOLARFLUX_GAMETEST_OK` on success and exits non-zero otherwise; `.github/workflows/game-test.yml`
checks that marker.

## Development environment ##

The Gradle wrapper, the GTNH build script and the CI workflows are all in the repository; a plain `./gradlew build`
works from a fresh clone.

Java 25 runs Gradle and Java 8 runs the test suite (that is the mod's bytecode target and the newest JVM Mockito 4.x
supports). Both are downloaded by the wrapper's toolchain support if they are missing.
