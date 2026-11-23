# SolarFlux Legacy — Code Review, Optimisations & Test Coverage Plan

> Analysis only: this document records findings and a plan. No production code was modified.
> Reviewed revision: `216a6c8` (OxiPNG image compression) on `master`, working tree clean.

## 0. Scope & baseline

Reviewed the whole shipped mod (`src/main/java`, 50 classes, 4,214 lines), the build harness
(`addon.gradle.kts`, `dependencies.gradle`, `gradle.properties`), the existing JUnit suite
(`src/test/java`, 21 source files) and the in-game test mod (`src/gameTest/java`). `build/rfg/minecraft-src`
(decompiled Forge/Minecraft) was used to confirm vanilla semantics; it is not part of the mod.

Commands run:

```
./gradlew test                    # 182 test cases, all green (~10 s warm)
./gradlew test jacocoTestReport   # report regenerated for the numbers below
```

Baseline measurements:

| Metric | Value |
| --- | --- |
| Test cases | 274 |
| Line coverage (JaCoCo, filtered) | 888 / 1490 = **59.6 %** |
| Branch coverage (filtered) | 290 / 456 = **63.6 %** |
| Coverage gate | 46 % line / 50 % branch (13.6 / 13.6 points below actual) |
| Excluded from the gate | `gui/**`, `core/gui/**`, `creativetab/**`, `ModConfigurationGui*`, `ConfigurationGuiFactory`, `Tags` |
| Server game-test checks | **158 / 158** passing (`./gradlew gameTestServer`) |
| Client game-test checks | **6 / 6** passing (`./gradlew runClient -PgameTest`) |
| Game-test coverage (separate report, server run) | 922 / 1481 lines = **62.3 %**, branches 40.2 % |

The suite is in good shape for its age: `StatefulEnergyStorage` (100 %), `BaseInventory` (97 %),
`ConnectedIconHandler` (97 %), `core.utility` (93 %) and `items` (79 %) are well covered. The gaps are
concentrated: `init.recipes` **0 %**, `blocks.modules` **69 %** (but `TraversalEnergyDispenserModule`
**14 %**), `config` **25 %**, `core.tileentity` **23 %**, `SolarPanelBlock` **50 %**,
`SolarPanelTileEntity` **68 %**.

**Fix first, in this order:** E5 (mixed-tier panels duplicate RF; enabled by default), I1 (shift-click
deletes upgrades), E1 (`extractEnergy` contract), I2 + G1 (GUI slot indexing and title), E3
(overflow), M1 (traversal duplicates). The two duplication/deletion findings are confirmed by
experiment (Appendix A).

---

## 1. Findings

Severity: **S1** = crash / data loss / energy duplication, **S2** = wrong behaviour, **S3** = latent
or configuration-dependent, **S4** = cosmetic / hygiene.

| # | Sev | Finding | Location |
| --- | --- | --- | --- |
| E1 | S1 | `extractEnergy` ignores `pMaxExtract` and `pFrom` — breaks the RF contract and lets energy be pulled from the "disconnected" top face | `SolarPanelTileEntity:304` |
| E2 | S2 | Generation is silently capped by the *transfer* rate, not the capacity | `SolarPanelTileEntity:184`, `StatefulEnergyStorage:44` |
| E3 | S1 | `autoBalanceEnergy(other, speed)` multiplies int transfer limits in place → overflow, permanent rate corruption, possible energy creation | `StatefulEnergyStorage:93-104` |
| E4 | S2 | Removing a capacity upgrade silently deletes stored RF above the new cap | `StatefulEnergyStorage:125-130` |
| E5 | **S1** | `autoBalanceEnergy` receives before it extracts and clamps each side independently → **adjacent panels of different tiers duplicate RF** (confirmed: +23,400 RF in one tick with the default configs) | `StatefulEnergyStorage:83-91` |
| E6 | S3 | Client-side `generateEnergy()` fabricates charge; the GUI drifts and snaps every 2 s | `SolarPanelTileEntity:91` |
| C1 | S2 | Tier loading stops at the first `Active=false`; disabling one tier silently drops all higher tiers and their blocks from existing worlds | `ModConfiguration:173-183` |
| C2 | S3 | `getTierConfiguration(int)` is an unchecked list index; any stale/foreign index throws | `ModConfiguration:276` |
| C3 | S3 | Runtime config reload only re-applies statics: block bounds, connected textures, tier list, block registration and existing panels' sharing module are not updated | `ModConfiguration:129-131` |
| M1 | S2 | Traversal search queues the same position more than once; the target cap is therefore not respected | `TraversalEnergyDispenserModule:60-70` |
| M2 | S3 | Traversal list is stale for the whole search (up to N × 25 ticks) and restarts immediately | `TraversalEnergyDispenserModule:25-42` |
| M3 | S3 | Furnace energy buffer is shared by all furnace neighbours and is lost on dismantle/upgrade | `SimpleEnergyDispenserModule:25,93-121` |
| M4 | S4 | Hard-coded 40-tick target refresh (`// TODO Should this be a config option?`) | `SimpleEnergyDispenserModule:41` |
| I1 | S1 | Shift-click upgrade path **deletes** the split stack when the merge fails | `ContainerSolarPanel:88-99` |
| I2 | S2 | `canDragIntoSlot` compares an *inventory* index with the upgrade-slot range → player hotbar slots 0–4 cannot be drag-filled | `ContainerSolarPanel:157` |
| I3 | S3 | `slotClick` overwrites the cursor after `super.slotClick`; any remainder is destroyed (reachable with an upgrade max > 64) | `ContainerSolarPanel:122-151` |
| I4 | S3 | `mergeItemStack` empty-slot branch ignores `ItemStack.getMaxStackSize()` | `BaseModContainer:140-146` |
| I5 | S4 | `transferStackInSlot` returns `null` after a partial move; `pAscending` actually means "reverse" | `ContainerSolarPanel:104-108`, `BaseModContainer:96` |
| I6 | S3 | `BaseInventory.readFromNBT` trusts the stored slot index (pinned as an AIOOBE test) | `BaseInventory:44` |
| I7 | S4 | `isItemValidForSlot(null, …)` NPEs; the check uses the held stack size, not the resulting slot size | `SolarPanelTileEntity:413-419` |
| I8 | S3 | `BaseInventory.setInventorySlotContents` notifies itself (no-op) instead of the owning tile entity; every caller must remember `TE.markDirty()` | `BaseInventory:104-112` |
| G1 | S2 | GUI title reads the **player's** inventory name → always "Inventory" | `GuiSolarPanel:46-52` |
| G2 | S4 | Hard-coded "Crafting component" and "Solar Panel"; `hasCustomInventoryName()` is always false, so nothing is localised | `CraftingItem:19`, `BaseInventory:21,107` |
| G3 | S4 | Config GUI exposes only `CATEGORY_GENERAL`; upgrades and tiers are file-only | `ModConfigurationGui` |
| R1 | S2 | Only BuildCraft wrenches can dismantle; TE/CoFH hammers and IC2 wrenches cannot | `Utils:44-54` |
| R2 | S4 | `getTileEntity(...)` cast without a null check in place/dismantle | `SolarPanelBlock:57-68,112-163` |
| R3 | S4 | `dropInventory` runs on the client as well (ghost item entities) | `BaseModBlockWithTileEntity:36-49` |
| R4 | S4 | `onBlockEventReceived` discards `super`'s return value | `BaseModBlockWithTileEntity:52-59` |
| R5 | S4 | Tile-entity registry name `"solar"` is not namespaced | `SolarFluxMod:40` |

### 1.1 Fix log

**Batch 1 — the S1 findings that a headless unit test can prove.** Each fix ships with regression
coverage; the seven tests below were confirmed to fail on the previous code and pass now.

| # | Fix | Files | Regression tests |
| --- | --- | --- | --- |
| E5 | The amount to move is computed once against **both** transfer rates, then the receiver is credited and the sender debited by exactly what was accepted | `StatefulEnergyStorage.autoBalanceEnergy` | `autoBalanceBetweenDifferentTiersNeverCreatesEnergy`, `autoBalanceBetweenDifferentTiersWithATransferSpeedNeverCreatesEnergy`, `autoBalanceAlwaysConservesEnergyAcrossTierCombinations` (4 × 4 × 4 × 4 × 2 combinations) |
| E3 | The speed overload no longer mutates the transfer limits; the scaled cap is computed in `long` | `StatefulEnergyStorage.autoBalanceEnergy(other, speed)` | `autoBalanceWithATransferSpeedNeverChangesLargeTransferLimits` (480,000,000 RF/t at speed 100) |
| E1 | `extractEnergy` honours `pMaxExtract` and the face, so the top face cannot be drained | `SolarPanelTileEntity.extractEnergy` | `extractEnergyIsLimitedByTheRequestAndByTheTransferRate`, `theTopFaceCannotExtractEnergy` |
| I1 | The part of the stack that did not merge is always added back instead of being deleted | `ContainerSolarPanel.transferStackInSlot` | `shiftClickingAnUpgradeIntoAFullPanelKeepsItInThePlayerInventory` (+ happy path, partial fit, non-upgrade) |

The private `removeEnergy` helper and the public `addEnergy` generation path never relax the public
`receiveEnergy`/`extractEnergy` contract; they only let `autoBalanceEnergy` apply the scaled cap
without temporarily changing the storage.

**Batch 2 — the S2 findings that a headless unit test can prove.** Nine tests were confirmed to fail
on the previous logic.

| # | Fix | Files | Regression tests |
| --- | --- | --- | --- |
| E2 | Generation now goes through `StatefulEnergyStorage.addEnergy` (capacity-clamped only) instead of `receiveEnergy`, so the tier's transfer rate no longer throttles production | `SolarPanelTileEntity.generateEnergy` | `generationIsNotCappedByTheTransferRate`, `generationIsStillCappedByTheFreeCapacity`, plus `addEnergy*` unit tests |
| E4 | A capacity shrink no longer deletes energy: stored energy may exceed the capacity and drains through the normal transfer path; headroom, `isFull` and the energy gauge are clamped to stay sane in that state | `StatefulEnergyStorage.getMaxReceive/setEnergyStored/setMaxEnergyStored/isFull`, `SolarPanelTileEntity.getScaledEnergyStoredFraction` | `removingACapacityUpgradeKeepsTheStoredEnergy`, `setMaxEnergyStoredKeepsTheEnergyThatNoLongerFits`, `theExcessAboveTheCapacityDrainsThroughTheNormalPath`, `theEnergyGaugeStaysFullWhenStoredEnergyExceedsTheCapacity`, `setEnergyStoredOnlyClampsNegativeValues` |
| I2 | Drag-and-drop is decided by slot ownership (`pSlot.inventory != panel`) instead of mixing an inventory index with container slot numbers | `ContainerSolarPanel.canDragIntoSlot` | `everyPlayerSlotCanBeDragFilledIncludingTheHotbar`, `upgradeSlotsCannotBeDragFilled` |
| M1 | The BFS checks the pending queue as well as the visited set, so a block reachable from two parents is queued once and the target list is published in the minimal number of rounds | `TraversalEnergyDispenserModule.discoverNeighbors` | `theSearchNeverQueuesTheSamePositionTwice`, `aBlockReachableFromTwoParentsIsPublishedOnce`, `theSearchStopsAtTheMaximumExtraTargets`, `withoutTraversalUpgradesOnlyTheDirectNeighboursAreVisited`, `anIsolatedPanelFinishesTheSearchWithoutThrowing` |

E4 is a deliberate policy change: `setEnergyStored` no longer clamps to the capacity (only negative
values are clamped), and `setMaxEnergyStored` never touches the stored energy. Energy can therefore
temporarily exceed the capacity, which is what makes removing a capacity upgrade lossless; the two
old tests that pinned the clamping were updated to pin the new policy. **Deferred S2s:** C1 needs the
`ConfigSource` seam from §6.2, G1 needs a booted client, and R1 needs the `WrenchHelper` extraction
(and the Forge API registry) — none are reachable from a headless unit test today.

**Batch 3 — the S3 findings that a headless unit test can prove.** Nine tests were confirmed to fail
on the previous logic; a tenth exercises the same clamp through the load path.

| # | Fix | Files | Regression tests |
| --- | --- | --- | --- |
| E6 | Generation moved inside the `isServer()` block; the client only mirrors the rate it receives from the container sync | `SolarPanelTileEntity.updateEntity` | `theClientDoesNotGenerateEnergy`, `theServerGeneratesEnergy` |
| C2 | `getTierConfiguration` clamps any index into the configured range and logs the fallback (the last tier is the closest match when a tier disappears); the panel's own getter delegates to it and clamps the stored index once on load | `ModConfiguration.getTierConfiguration/clampTierIndex`, `SolarPanelTileEntity.getTierConfiguration/loadDataFromNBT` | `anOutOfRangeTierIndexFallsBackToTheLastTier`, `aNegativeTierIndexFallsBackToTheFirstTier`, `anUnknownTierIndexFallsBackToAValidTier`, `aNegativeTierIndexFallsBackToTheFirstTier` (panel), `aSavedTierIndexOutOfRangeIsClampedOnceOnLoad` |
| I6 | `readFromNBT` skips slot indexes outside the inventory instead of throwing away the whole load | `BaseInventory.readFromNBT` | `readFromNbtIgnoresSlotsBeyondTheInventorySize`, `readFromNbtIgnoresNegativeSlotIndexes` |
| I8 | `BaseInventory` takes an optional owner and forwards `markDirty()`, so a slot change refreshes the panel's upgrade cache without every caller having to remember | `BaseInventory`, `SolarPanelTileEntity` | `changingASlotNotifiesTheOwner`, `theUpgradeCacheIsRefreshedWithoutAnExplicitMarkDirty` |

**Deferred S3s:** C3 needs the same `ConfigSource` seam (it re-reads a Forge `Configuration`); M2 is a
design choice rather than a defect (keeping the previous target list until a search completes is
intentional; only the immediate restart cadence remains).

**Batch 4 — the last S3s that a headless unit test can prove.** Five tests were confirmed to fail on the
previous logic.

| # | Fix | Files | Regression tests |
| --- | --- | --- | --- |
| I3 | `slotClick` combines the withheld part with whatever vanilla could not place instead of overwriting the cursor | `ContainerSolarPanel.slotClick` | `slotClickKeepsThePartThatDoesNotFitInTheSlot`, `slotClickOnAFullSlotKeepsEveryItemOnTheCursor` |
| M3 | The furnace prepayment moved onto the panel, so it survives the dispenser-module swap caused by a traversal upgrade, is saved to NBT, and is added to the energy carried by the dropped item | `SolarPanelTileEntity`, `SimpleEnergyDispenserModule`, `SolarPanelBlock`, `NBTConstants` | `theFurnaceBufferSurvivesTheModuleSwapCausedByATraversalUpgrade`, `theFurnaceEnergyBufferIsPersistedAndClamped`, `dismantlingKeepsTheBufferedFurnaceEnergy` |

The buffer is still shared by all furnace targets. That part is intentional: it is a panel-level
prepayment for the furnace upgrade, and each grant is consumed by whichever furnace needs it first.
The defect was that the prepayment vanished when the module was replaced or the panel dismantled.

**Not addressed in that batch:** C1 needs the `ConfigSource` seam from §6.2 *and* a policy decision (a
non-contiguous `Active=false` cannot be honoured without either renumbering existing panels or keeping
placeholder blocks); G1 needed a client (closed in batch 7 by the client harness); M2 is documented
behaviour, so only a design change remains. C3 and R1 were later covered by the server game test
(batch 6), and I4 by a unit test in batch 7.

**Batch 5 — the S4 findings that a headless unit test can prove.** Eleven tests were confirmed to fail on
the previous logic.

| # | Fix | Files | Regression tests |
| --- | --- | --- | --- |
| M4 | The dispenser target refresh is a config option (`DispenserUpdateRate`, default 40) instead of a hard-coded `2 * 20` | `ModConfiguration`, `SimpleEnergyDispenserModule` | `theTargetRefreshRateComesFromTheConfiguration` |
| I5 | `transferStackInSlot` reports a transfer whenever anything moved, including the upgrade-specific partial move; `mergeItemStack`'s flag is renamed to `pReverse`, which is what it does | `ContainerSolarPanel.transferStackInSlot`, `BaseModContainer.mergeItemStack` | `transferStackInSlotReportsAPartialUpgradeMove`, `transferStackInSlotReportsAFullMove` |
| I7 | `isItemValidForSlot` rejects a null stack instead of throwing | `SolarPanelTileEntity.isItemValidForSlot` | `isItemValidForSlotRejectsANullStack`, `isItemValidForSlotFollowsThePerPanelMaximum` |
| G2 | The crafting tooltip and the panel inventory name go through `Lang`/the lang file; the name is a key, so `hasCustomInventoryName()` correctly stays false | `CraftingItem`, `SolarPanelTileEntity`, `en_US.lang` | `theTooltipIsLocalisedThroughLang`, `theTooltipKeyExistsInTheEnglishLangFile`, `thePanelTitleKeyExistsInTheEnglishLangFile`, `theDefaultConstructorStartsAtTierZero` |
| R2 | Placement and dismantling check the tile entity type before casting | `SolarPanelBlock` | `placingAPanelWithoutATileEntityDoesNotThrow`, `dismantlingAPanelWithoutATileEntityDropsTheItem` |
| R3 | `dropInventory` does nothing on the client, so no ghost item entities | `BaseModBlockWithTileEntity` | `theClientDoesNotDropTheInventory` |

**Remaining S4s:** see §1.2 for the per-item game-test verdict.

**Batch 6 — findings that needed a real Forge launch, covered by the in-game test mod.** Four new checks
were confirmed to fail on the previous logic (127/131 before the fix, 131/131 after).

| # | Fix | Files | Tests |
| --- | --- | --- | --- |
| C3 | Runtime reload re-applies the restart-safe values and the panel bounds, and deliberately does not rebuild the tier list; the log line tells pack authors that tier/registration changes need a restart | `ModConfiguration.reload`, `ModBlocks.refreshBlockConfiguration`, `SolarPanelBlock.applyBlockBounds` | game test `checksRuntimeReload`: height applied, tier list identity unchanged, block bounds re-applied |
| R1 | `hasUsableWrench` also accepts a CoFH `IToolHammer` (Thermal Expansion's hammer); the BuildCraft path keeps its API gate | `Utils.hasUsableWrench` | game test `checksWrench`: hammer counts as a wrench and dismantles, a plain item does not; unit `aCoFHHammerCountsAsAWrench`, `aPlainItemIsNotAWrench` |

### 1.2 Game-test verdict for every remaining finding

The harnesses are a dedicated server (`./gradlew gameTestServer`) and a dev client
(`./gradlew runClient -PgameTest`): real Forge/FML and registries on both, a real world on the server,
and the real GUI plus language on the client. That decides each item:

| # | Sev | Verdict |
| --- | --- | --- |
| C1 | S2 | **Done in batch 8.** Tiers are parsed up to the last *configured* tier instead of stopping at the first inactive; each tier keeps its index and block, and an inactive tier only loses its recipe. |
| C3 | S3 | **Done in batch 6.** |
| I4 | S3 | **Done in batch 7** (unit test; no harness needed). |
| G1 | S2 | **Done in batch 7** (client test). |
| R1 | S2 | **Closed by decision.** CoFH hammers are supported and game-tested (batch 6); BuildCraft works behind its API gate. IC2 support is out of scope (it would add a new dependency for one interface). |
| M2 | S3 | **Won't fix (by design).** Keeping the previous target list until a search completes is deliberate; only the cadence is a design choice. |
| G3 | S4 | **Done in batch 7** (client test). |
| R4 | S4 | **No action.** `Block.onBlockEventReceived` always returns `false`, so there is no super return value to observe. |
| R5 | S4 | **Done in batch 8** (legacy alias + canonical `solarflux:solar`). |

**Remaining after these decisions: none.** Every S1–S4 finding is fixed, closed by decision, or has no
observable effect.

**Batch 7 — the remaining testable items, including a client harness.** New regression coverage: the unit
test for I4 and two client assertion groups (six checks).

| # | Fix | Files | Tests |
| --- | --- | --- | --- |
| I4 | `mergeItemStack`'s empty-slot branch caps at `min(item max stack size, slot limit)`, like vanilla | `BaseModContainer.mergeItemStack` | unit `mergeItemStackHonoursTheItemMaxStackSize` |
| G1 | The panel GUI title is the panel's localised name; `GuiSolarPanel.getPanelTitle()` is extracted so it can be asserted without rendering | `GuiSolarPanel` | client `checkPanelTitle` (title equals the localised panel name, is not "Inventory", and is actually translated) |
| G3 | The config GUI passes every category (general, upgrades, one per tier) instead of only general | `ModConfiguration.getConfigGuiElements`, `ModConfigurationGui` | client `checkConfigurationGui` (the `GuiConfig` instance carries the general, upgrades and tier categories) |

**Batch 8 — C1 and R5.** The tier-activation rework and the tile-entity id migration.

| # | Fix | Files | Tests |
| --- | --- | --- | --- |
| C1 | Tiers are parsed up to the last *configured* tier (hard cap 16) instead of stopping at the first `Active=false`; `TierConfiguration` carries an `active` flag, the list and the block list stay index-aligned, and an inactive tier keeps its block (and any existing world) while losing only its recipe and its creative-tab entry. Configs that already contain `solar_panel_tier6` gain a seventh inactive, recipe-less, creative-hidden block; indices 0–5 are untouched. | `TierConfiguration`, `ModConfiguration.parseTierConfigurations`, `ModBlocks`, `VanillaRecipeRegistrar` | game `checksInactiveTierHandling` (crafted gap config: six tiers parsed, tier 2 inactive with its own values, no tier-2 recipe, tier 1/3 recipes still registered); unit `theActiveFlagIsKept` |
| R5 | The tile entity is registered as `solarflux:solar`, with `solar` kept as a legacy alias (one class under two ids; `classToNameMap` is last-wins), so new saves are namespaced while old saves resolve | `SolarFluxMod` | game `checksTileEntityId` (new NBT id, legacy id loads, charge preserved) |

**Modid rename — considered for R5, rejected.** 1.7.10 persists a per-world registry name→id map
(`GameData.injectWorldIDMap`), and names that are no longer registered are removed unless the mod
answers `FMLMissingMappingsEvent`. Renaming `SolarFlux` → `solarflux` changes every block and item
registry name, so it would need ~30 remaps, a config-file move (`SolarFlux.cfg` → `solarflux.cfg`), and
would still trigger the world's "different mods" warning and break third-party `after:SolarFlux`
declarations. The tile-entity id is independent of the modid and is namespaced above.

### 1.3 Client test harness

The client half runs on a real dev client at the main menu, where the client-only classes are loaded
and the language file is active:

```
./gradlew runClient -PgameTest
```

`GameTestMod` registers a client tick hook (stripped on the server) that waits for `GuiMainMenu`, runs
`ClientGameTests`, logs `SOLARFLUX_CLIENTTEST_OK`/`FAILED`, and exits the client with the result. It is
how the two GUI findings are closed without a human looking at the screen.

Both harnesses write the same `build/jacoco/gameTest.exec`, so generate `jacocoGameTestReport` after the
run whose coverage you want (the numbers above are from the server run).

Non-issues verified against vanilla (see Appendix B): `setLightOpacity(255)` on a partial block, the
top face rejecting connections, the `solar0` name vs `solar1` registry name, and the metadata-less
`getIcon` fallback are all deliberate or harmless.

---

### S1–S3 details

#### E1 — `extractEnergy` violates the `IEnergyProvider` contract

```java
// SolarPanelTileEntity:304
public int extractEnergy(ForgeDirection pFrom, int pMaxExtract, boolean pSimulate) {
    return getEnergyStorage().extractEnergy(getEnergyStorage().getMaxExtract(), pSimulate);
}
```

Two violations:

* `pMaxExtract` is ignored. A conduit asking for 100 RF/t gets up to the panel's whole transfer
  budget (32,768 RF/t on tier VI). RF conduits, energy cells and other mods negotiate flow with the
  requested amount; this over-delivers and can overflow a neighbour's per-tick transfer logic.
* `pFrom` is ignored, and `canConnectEnergy(UP)` is `false`. Any code that ignores
  `canConnectEnergy` (a surprisingly common shortcut) can drain the panel through the face the mod
  says is disconnected.

The existing test `extractEnergyIsLimitedByTheStorageTransferRateAndNotByTheRequest` pins this
behaviour deliberately, which is exactly why the fix should update that test rather than leave the
contract broken. Correct body:

```java
if (!canConnectEnergy(pFrom)) return 0;
return getEnergyStorage().extractEnergy(pMaxExtract, pSimulate);
```

#### E2 — production is throttled by the transfer rate

`generateEnergy()` feeds the storage through `receiveEnergy`, which clamps to
`min(capacity - energy, mMaxTransferReceive)`. `TierConfiguration` documents
`MaximumEnergyTransfer` as "RF transferred to adjacent machines per tick", so it should not limit
production. The shipped tiers hide the bug because the second `TierConfiguration` constructor sets
`transfer = generation * 8` (`TierConfiguration:20-24`). A pack that sets e.g.
`MaximumEnergyGeneration=10000` and `MaximumEnergyTransfer=1000` gets a panel that produces 1,000 RF/t
and no hint why. Fix: add `StatefulEnergyStorage.addEnergy(int)` (capacity-clamped only) and use it
from `generateEnergy`.

#### E3 — int overflow in the energy-sharing module (confirmed experimentally)

```java
// StatefulEnergyStorage:93
mMaxTransferExtract *= pTransferSpeed;      // 480,000,000 * 100 -> 48,000,000,000 (overflow)
...
mMaxTransferExtract /= pTransferSpeed;      // (wrapped int) / 100 does not restore the original
```

`EnergySharingModule` always calls this with `DISTRIBUTION_TICK_RATE = 100`. Any panel with a
transfer rate above 21,474,836 RF/t (the config allows up to 2,000,000,000) is corrupted on its first
balance tick. I reproduced it with a throw-away JUnit test (since deleted):

```
before: extract=480000000 receive=480000000
after:  extract=7553597   receive=7553597      // 480,000,000 * 100 wrapped, / 100 truncated
```

The corruption is permanent until something calls `setMaxTransfer` again, and because
`getMaxReceive()`/`getMaxExtract()` can go **negative** on the wrapped value, `receiveEnergy` then
*subtracts* energy and `extractEnergy` *adds* it. Prefer not mutating at all:

```java
private int transferTo(StatefulEnergyStorage pOther, int pCap) {
    int delta = getEnergyStored() - pOther.getEnergyStored();
    if (delta <= 0) return 0;
    int move = Math.min(Math.min(delta / 2, pCap), pOther.getMaxReceive());
    return extractEnergy(pOther.receiveEnergy(move, false), false);
}
```

If the in-place mutation is kept, do it in `long` and restore in a `finally`.

#### E5 — energy duplication between different tiers (confirmed experimentally)

```java
// StatefulEnergyStorage:83
public int autoBalanceEnergy(StatefulEnergyStorage pOtherEnergyStorage) {
    int delta = getEnergyStored() - pOtherEnergyStorage.getEnergyStored();
    if (delta < 0) return pOtherEnergyStorage.autoBalanceEnergy(this);
    else if (delta > 0 && !pOtherEnergyStorage.isFull()) {
        return extractEnergy(pOtherEnergyStorage.receiveEnergy(delta / 2, false), false);
    }
    return 0;
}
```

The receiver is credited first and the sender is then clamped **independently**. When the sender has
the smaller transfer rate, it extracts less than the receiver was already given.
`EnergySharingModule` is enabled by default (`BalanceEnergy=true`), so two adjacent panels of
different tiers are enough. Throw-away test (since deleted), default tier data, speed 100:

```
// tier I (capacity 50,000, transfer 16) next to tier VI (capacity 60M, transfer 32,768)
before: rich=50000 poor=0
poor.receiveEnergy(25000) -> 25000          // receiver's cap is 3,276,800
rich.extractEnergy(25000) -> 1600           // sender's cap is 1,600
after:  rich=48400 poor=25000 total=73400   // +23,400 RF from nothing
```

Same-tier pairs are fine (the caps match); mixed-tier pairs create RF every balance tick, forever.
The `autoBalanceEnergy(other, speed)` overload does not fix it: with `speed=1` (the plain overload) it
is `rich=49984 poor=25000 total=74984` (+24,984), and with `speed=100` it is
`rich=48400 poor=25000 total=73400` (+23,400). Fix by computing the move once against **both** caps:

```java
int move = Math.min(delta / 2, Math.min(getMaxExtract(), pOther.getMaxReceive()));
int moved = pOther.receiveEnergy(move, false);
return extractEnergy(moved, false);   // moved <= both caps, so the two sides always agree
```

The same receive-first ordering in `sendMaxTo` (`StatefulEnergyStorage:76-78`) is safe for a
well-behaved receiver because the request is already capped by the sender, but it shares the
"state changes before the counter-change is known to succeed" shape. Extract-then-receive, or
simulate first, makes the invariant obvious.

#### I1 — shift-click item deletion

```java
// ContainerSolarPanel:92
int canAdd = Math.min(mSolarPanelTileEntity.additionalUpgradeAllowed(slotItemStack), slotItemStack.stackSize);
if (canAdd > 0) {
    ItemStack merging = slotItemStack.splitStack(canAdd);
    if (mergeItemStack(merging, 0, SolarPanelTileEntity.INVENTORY_SIZE, false)) {
        slotItemStack.stackSize += merging.stackSize;   // only on success
    }
    ...
```

`splitStack` already removed `canAdd` items from the source slot. If `mergeItemStack` returns
`false`, `merging` goes out of scope and the items are gone. That is **normal gameplay**, not an edge
case: give a panel five *different* upgrades (one per slot; every slot accepts any `UpgradeItem`
because `isItemValidForSlot` only checks the item type and the per-panel count) and shift-click a
sixth type. `additionalUpgradeAllowed` returns a positive value, the stack is split, no slot matches
and no slot is empty, so `mergeItemStack` returns false and the split stack is deleted. Always add
the remainder back:

```java
if (!mergeItemStack(merging, 0, INVENTORY_SIZE, false)) {
    slotItemStack.stackSize += merging.stackSize;   // nothing moved
} else {
    slotItemStack.stackSize += merging.stackSize;   // leftovers
}
```
i.e. `slotItemStack.stackSize += merging.stackSize;` unconditionally, before the `putStack` block.

#### I2 — drag-and-drop vs hotbar slots

`BaseModContainer.addPlayerActionSlotsToContainer` creates player hotbar slots with inventory index
`0..8`. `canDragIntoSlot` uses `pSlot.getSlotIndex()` (the index *inside its inventory*), so hotbar
slots 0–4 look like the panel's upgrade slots 0–4:

```java
return pSlot.getSlotIndex() >= SolarPanelTileEntity.INVENTORY_SIZE;   // wrong axis
```

The upgrade slots are container indices `0..4`; hotbar slot 5+ is fine, 0–4 is not. Use the container
index (`pSlot.slotNumber`) or compare `pSlot.inventory != mSolarPanelTileEntity`. This is the same
"two kinds of index" mistake that the `// TODO add a check for slot -999` in `slotClick` is guarding
against; after the fix, `slotClick` can keep using the container index (it already does).

#### C1 — tier truncation silently deletes higher tiers

```java
// ModConfiguration:173
for (int tier = 0;; ++tier) {
    TierConfiguration config = loadTierConfiguration(tier);
    if (config == null) break;      // stops at the first inactive tier
    mTierConfigurations.add(config);
}
```

`loadTierConfiguration` returns `null` for `Active=false`. Consequences:

* A pack that disables `solar_panel_tier1` and keeps `tier2..tier5` enabled registers **one** panel;
  the other five are silently dropped. On an existing world the corresponding blocks are no longer
  in the registry, so Forge replaces them with air on chunk load (at best a generic "missing block"
  log line) — world data loss from a config toggle.
* Tier *identity* is the list position (`mTierIndex`), so the mapping between a block, its stats and
  its texture is positional. Any future support for sparse tiers must therefore store the tier id in
  the block/TE rather than deriving it from the index.

Minimum fix: keep scanning to a hard cap (e.g. 32/64) and log a warning for every disabled tier that
has higher tiers enabled; better, make the tier list dense *and* make `getTierConfiguration` fall back
safely (C2), and document "disabling a tier is destructive".

#### C2 — unchecked tier index

`getTierConfiguration(int)` is called from the TE constructor, from `loadDataFromNBT` (via
`markDirty → refreshUpgradeCache`), from the per-second generation update, and from the item tooltip.
Any index outside the list throws `IndexOutOfBoundsException`; from `updateEntity` that is a server
crash (chunk reload loop). This is reachable through a stale `SFTierIndex` in NBT (a world created
with more tiers, then a config that removes them), through `SolarPanelItemBlock.addInformation` if a
tier is removed while items sit in inventories, and through third-party code calling the public
`getTierConfigurations()`/`getTierConfiguration()` API. Clamp and log once:

```java
if (pTierIndex < 0 || pTierIndex >= mTierConfigurations.size()) {
    // log once, then fall back to the closest tier
}
```

#### M1 — traversal duplicates (confirmed experimentally)

`discoverNeighbors` only checks `mVisitedBlocks`, never `mBlocksToVisit`. With a diamond of receivers
I observed a position that was already in `mVisitedBlocks` still sitting in the queue:

```
targets=3  queue=[[1,64,-1]]
visited=[[1,64,0], [0,64,-1], [1,64,-1]]
directNeighbourDiscovered=2
```

The duplicate is popped later, re-added to the visited set (no-op) and re-scans its neighbourhood, so
it costs ticks and — more importantly — inflates `mDirectNeighborDiscovered`, which is used as the
"free" part of `mVisitedBlocks.size() >= mDirectNeighborDiscovered + getMaximumExtraTargets()`. The
`MaximumExtraTargets` cap therefore leaks by the number of duplicates. Fix: keep a `Set` of queued
positions, or use `mBlocksToVisit.contains(neighbor)` (a `LinkedHashSet` gives O(1) and stable
ordering).

#### G1 — GUI title

```java
// GuiSolarPanel:46
fontRendererObj.drawString(
    mInventoryPlayer.hasCustomInventoryName() ? mInventoryPlayer.getInventoryName()
        : I18n.format(mInventoryPlayer.getInventoryName()), ...);
```

`InventoryPlayer.getInventoryName()` is `"container.inventory"` and `hasCustomInventoryName()` is
`false`, so the title is always "Inventory". It should render the panel's name
(`mSolarPanelTileEntity.getInventoryName()`), localised. That requires G2 (`"Solar Panel"` is a
literal and `hasCustomInventoryName()` returns `false` in `BaseInventory`).

---

## 2. Performance & tick-path optimisations

1. **Sync the whole inventory on every chunk load.** `BaseModTileEntitySynced.getDescriptionPacket`
   calls `addDataToNBT`, which writes the tier, the item list **and** the energy into the S35 packet.
   For a field of panels this sends every upgrade stack to every client. Split the class into
   `addDataToNBT` (disk) and `addSyncDataToNBT` (energy, generation, sun, tier) — this is also a
   prerequisite for WAILA (feature 1).
2. **Skip generation on the client.** `updateEntity` calls `generateEnergy()` before the `isServer()`
   guard (E6). One branch per tick per panel, plus a client-side desync.
3. **Cache the target tile entities, not just the positions.** `SimpleEnergyDispenserModule` calls
   `World.getTileEntity` once in `sendEnergyToTargets` (indirectly, via `sendEnergyTo`) for every
   target every tick, and six more times every 40 ticks. A `List<BlockPosition>` plus a lazily
   resolved `IEnergyReceiver` (invalidated on the 40-tick refresh) removes ~N lookups per tick per
   panel. With a 1,000-panel field and 4 neighbours this is thousands of chunk lookups per tick.
4. **Traversal discovery should have a per-refresh budget, not one node per 1.25 s.** Today a
   64-upgrade panel needs 1,600 ticks (80 s) to complete one sweep and immediately starts the next.
   Completing a sweep in one pass with a hard node cap (say 512) is cheaper and gives a correct target
   list every `UpdateRate`. Keep a `LinkedHashSet` for the queue (M1).
5. **Don't rebuild the upgrade cache on every `markDirty`.** `SolarPanelTileEntity.markDirty`
   refreshes the whole cache (a slot scan plus `Math.pow`) and recomputes capacity/transfer. It is
   called on every shift-click merge (`slot.onSlotChanged`). Cache the last inventory fingerprint
   (five slot references + sizes) and skip if unchanged, or refresh only in `setInventorySlotContents`
   when the stack actually changes.
6. **Avoid `Math.pow` in the tick path.** `computeSunIntensity` calls `getUpgradeCount` twice per
   second (cheap), but `refreshUpgradeCache` uses `Math.pow` per capacity/transfer refresh. Precompute
   the multiplier when the upgrade count changes (it only depends on the count).
7. **`SimpleEnergyDispenserModule.sendEnergyToTargets` fairness.** The round-robin index works, but a
   full target absorbs everything, so the first target is over-served for a tick. Splitting the
   available energy across targets (`available / targets`) is one division per tick and removes the
   bursty behaviour on small buffers.
8. **`GuiSolarPanel`** rebuilds four `String.format`s every frame (60 Hz). Cache them and refresh on
   the container's progress-bar updates (which already happen at most every tick).
9. **Minor:** `BlockPosition` allocations in `searchTargets` (7 per 40 ticks) are negligible; don't
   bother. `mUpgradeCache` as `HashMap<Item, Integer>` is fine, but `int[]` indexed by an enum would
   avoid boxing. Only do this if profiling shows it.

### 2.1 Perf batch P1 (done)

| # | Change | Where | Tests |
| --- | --- | --- | --- |
| 1 | The S35 description packet carries only tier, energy, capacity, generation and sun; the inventory stays on disk. `BaseModTileEntitySynced` gained overridable `addSyncDataToNBT`/`loadSyncDataFromNBT` (defaulting to the full data for other TEs) plus `getSyncTag`/`applySyncTag` seams whose names do not differ between the dev and production mappings. | `BaseModTileEntitySynced`, `SolarPanelTileEntity`, `NBTConstants` | unit `theDescriptionPacketCarriesOnlyTheDisplayState`, `applyingTheDescriptionPacketLeavesTheClientInventoryAlone`; game `checksNbtRoundTrip` (no `ITEMS`, capacity/tier present, no disk-only keys, real S35 packet) |
| 2 | Skip generation on the client | already done with E6 | — |
| 3 | Targets resolve their tile entity once per refresh, in a map keyed by `BlockPosition` that is dropped whenever the target list is rebuilt | `SimpleEnergyDispenserModule`, `TraversalEnergyDispenserModule` | unit `aTargetTileEntityIsResolvedOncePerRefresh` (2 world lookups over 3 sends instead of 4), `aReplacedTargetIsPickedUpAfterTheNextRefresh`, traversal `aRepublishedTargetListDropsTheResolvedTileEntities` |

**P2 (done)**

| # | Change | Where | Tests |
| --- | --- | --- | --- |
| 4 | One `searchTargets` call completes a sweep (node budget 512) instead of one node per update rate; the queue is a `LinkedHashSet` polled in insertion order | `TraversalEnergyDispenserModule` | `oneRefreshCompletesTheWholeSweep`, `aBlockReachableFromTwoParentsIsValidatedOnce` |
| 5/6 | The upgrade cache remembers the slot references and sizes and skips the rebuild (and its `Math.pow`s) when an unrelated `markDirty` fires; an in-place stack growth still triggers it, and a configuration version bump forces it so a reload is never masked by unchanged slots | `SolarPanelTileEntity`, `ModConfiguration` | `theUpgradeCacheIsOnlyRebuiltWhenTheInventoryChanges`, `anInPlaceStackSizeChangeStillRefreshesTheCache`, `aConfigurationVersionChangeRefreshesTheCacheEvenWithUnchangedSlots` |
| 7 | Energy is shared across targets (`available / targets`) instead of the first target draining a small buffer; a full panel still sends the full transfer rate to each | `SimpleEnergyDispenserModule`, `StatefulEnergyStorage.sendTo` | `aSmallBufferIsSplitEvenlyAcrossTheTargets`, `aFullBufferStillSendsTheTransferRateToEveryTarget`, `sendToIsCappedByTheGivenAmount`, `sendToIsAlsoCappedByTheTransferRate`, `sendToTreatsANegativeCapAsZero` |

All §2 items are now done except 8/9, which are deliberately skipped (negligible cost / profiling-only).

---

## 3. Refactors

1. **Extract the sun model into a pure class.** `computeSunIntensity` mixes a `World` query, the
   celestial angle, two upgrade counts and three config factors. A
   `SunIntensity.calculate(canSeeSky, celestialAngleRadians, isRaining, isThundering, lowLightCount, config)`
   returning `float` is unit-testable without a single mock and makes the GUI/game-test assertions
   exact. This is the single highest-leverage refactor for coverage.
2. **Extract the upgrade maths.** `SolarPanelTileEntity.refreshUpgradeCache` is inventory scanning +
   config + storage mutation + module swapping in one method. Split into
   `UpgradeCounts.scan(IInventory)`, `UpgradeMath.capacity(tier, count)`, `UpgradeMath.transfer(tier, count)`
   and a small `UpgradeEffects.apply(...)`. Each piece is trivially testable, and the `Math.pow` /
   `(int)` cast is in one place.
3. **Introduce a `ConfigSource` seam.** `ModConfiguration` is 400 lines of statics that cannot be
   loaded headlessly because `new Configuration(File)` needs FML. A minimal
   `interface ConfigSource { boolean getBoolean(...); int getInt(...); float getFloat(...); }` with a
   Forge adapter and a `Map`-backed fake would make `loadConfiguration`, `loadTierConfigurations` and
   the derived tier defaults unit-testable (≈120 lines, C1/C2 regression tests). It also gives the
   config GUI a single source of truth (G3).
4. **Split `SolarPanelTileEntity`.** It is 427 lines and owns energy, inventory, NBT, upgrades,
   weather and modules. At minimum: `PanelEnergy` (storage + generation + sun), `PanelUpgrades`
   (cache + maths), `PanelSync` (NBT/packet). Keep the `IInventory`/`IEnergyProvider` façade in the TE.
5. **Make `BaseInventory` notify its owner.** Replace the no-op `markDirty()` with a `Runnable`
   change listener (or have `SolarPanelTileEntity.setInventorySlotContents` call `markDirty()`).
   That removes I8, the manual `markDirty()` calls in `SolarPanelBlock`, and the "tests must call
   markDirty" caveat that is documented in `SolarPanelTileEntityTest`.
6. **Remove the `BaseModContainer.mergeItemStack` override.** Vanilla 1.7.10 already merges into
   existing stacks and empty slots, honours `Slot.isItemValid` and `ItemStack.getMaxStackSize`, and
   is the code path every other mod has exercised. The override is ~65 lines, re-implements the
   merge imperfectly (I4) and its loop conditions are hard to review. If it must stay,
   rewrite it as two clearly named helpers (`mergeIntoExisting`, `mergeIntoEmpty`) and add the tests
   in §6.
7. **Normalise the slot vocabulary.** `SolarPanelTileEntity.UPGRADE_SLOTS` is an inventory range,
   `ContainerSolarPanel` receives container slot numbers, and `Slot.getSlotIndex()` is an inventory
   index. Name the variables (`containerSlot`, `inventorySlot`, `slotNumber`) or wrap the panel's
   slots in `SlotUpgrade` helpers (`isUpgradeSlot(Slot)`) so I2 cannot recur.
8. **Logging.** `LogHelper` wraps `FMLLog` and exposes `all/trace/off/fatal` that nothing calls
   (see §4). 1.7.10 already ships Log4j2; `LogManager.getLogger(Reference.MOD_ID)` gives the same
   result with a real logger, `{}` placeholders and no dependency on FML internals. Also replace the
   nine `e.printStackTrace()` calls in `ThermalExpansionRecipeRegistrar` with
   `log.warn("…", e)`; stack traces on stdout bypass log rotation and are invisible in crash reports.
9. **Consistent null/precondition style.** `java.util.Objects.requireNonNull` is used in the icon
   handlers, Guava `Preconditions` in `BaseModBlock`/`BaseModItem`, and hand-rolled `!= null` checks
   in between. Pick one (Java 8 `Objects` where possible) — this is also what Checkstyle/Spotless
   will thank you for.
10. **Move `Utils.isShiftKeyDown` out of `Utils`.** It touches `org.lwjgl.input.Keyboard` (client
    only) from a class loaded on the server. It works because the reference is inside a method body,
    but it is a footgun: one refactor that moves the call into a constructor is a `NoClassDefFoundError`
    on dedicated servers. A `ClientUtils` class (or `SolarPanelItemBlock` directly) is safer.

### 3.1 Refactor/dead-code batch R1 (done)

| # | Change | Tests |
| --- | --- | --- |
| 1 | Sun model extracted to `SunIntensity.calculate(...)`; the tile entity only supplies the inputs | `SunIntensityTest` (noon, midnight, exact π/2, night fold, rain/thunder, low-light window) |
| 2 | Upgrade maths extracted to `UpgradeMath.apply/multiplier`, used for transfer rate, capacity and efficiency | `UpgradeMathTest` |
| 5 | Already delivered with I8 (`BaseInventory` owner → `markDirty`) | — |
| 6 | **Kept deliberately.** 1.7.10's vanilla `Container.mergeItemStack` does **not** call `Slot.isItemValid` in its existing-stack loop, so removing the override would let the normal merge path grow an upgrade stack past the per-panel maximum. | existing container tests |
| 10 | `isShiftKeyDown` moved to `ClientUtils`; `Utils` no longer references LWJGL | — |

**R2 (done)**

| # | Change | Tests |
| --- | --- | --- |
| 7 | Slot vocabulary: container-slot parameters and `isUpgradeSlot(int)` in `ContainerSolarPanel`; `slotClick` reads through the `Slot` instead of assuming container slot N is inventory slot N; `UPGRADE_SLOTS` removed | existing container tests |
| 8 | `LogHelper` deleted; `SolarFluxMod.log` is a Log4j2 `Logger`, `%s`/`%d` converted to `{}`, the nine `printStackTrace()` calls replaced by `log.warn(..., e)` | `spotlessCheck` + the game/client launches exercise the logger |
| 9 | One null-check style: `Objects.requireNonNull` everywhere, `checkArgument`/`checkState` replaced by explicit exceptions | compilation + the existing tests |

**R3 (done)**

| # | Change | Tests |
| --- | --- | --- |
| 3 | `ConfigSource` seam: `ForgeConfigSource` adapts the real file, `MapConfigSource` is the in-memory fake; `loadRestartSafeConfiguration`, `loadUpgradesConfiguration` and `parseTierConfigurations` now take a source instead of reading the static `Configuration` | `ModConfigurationLoadTest` (map source drives every general/upgrade value, defaults on an empty source, gap in Active flags keeps the indices, derived tier beyond the defaults) |

**R4 (done)**

| # | Change | Tests |
| --- | --- | --- |
| 4 | Split the tile entity: `PanelEnergy` owns the storage and display values, `PanelUpgrades` owns the slot scan/counts (with the change snapshot), `PanelSync` owns the S35 payload. The tile entity keeps the `IInventory`/`IEnergyProvider` façade, the world-dependent sun calculation, the module wiring and the disk NBT composition. 516 → 456 lines even with the new delegations. | `PanelEnergyTest`, `PanelUpgradesTest` (summed counts, unchanged slots skipped, in-place size growth, replacement, non-upgrades), `PanelSyncTest` (round trip, no inventory/disk keys, empty payload); all existing unit/game/client checks unchanged |

**§3 is complete.** All ten refactors are done or, for #6, kept with a documented reason.

---

## 4. Dead code / unused surface

| Item | Evidence | Action |
| --- | --- | --- |
| `WaferSawItem` | Only referenced by its own class and `UpgradeItemTest`; never constructed or registered | **Deleted in R1** (and its two tests) |
| `MetricUnits.TERA` | No references | **Deleted in R1** (+ test assert) |
| `Color.BLACK`, `Color.WHITE` | Defined but never used | **Deleted in R1** (+ test asserts) |
| `BlockPosition.forward()` | No references (only `move()` is used) | **Deleted in R1** (+ its test) |
| `BaseModContainer` unused click constants | Only `CLICK_MODE_NORMAL`, `MOUSE_LEFT_CLICK` and `MOUSE_RIGHT_CLICK` are used | **Deleted in R1** (the three used ones stay) |
| `LogHelper.all/trace/off/fatal` | No callers | **Class deleted in R2**; calls use `SolarFluxMod.log` (Log4j2) directly |
| `StatefulEnergyStorage(int)` / `(int,int,int)` | Production only ever calls the 2-arg constructor (`SolarPanelTileEntity:70`); the 1-arg and 3-arg forms exist only for tests | **Documented in R4** as test/harness conveniences |
| `TierConfiguration(int, int)` | Used only by `DEFAULT_TIER_CONFIGURATIONS`; the `generation * 8` rule is implicit and is the root of E2's invisibility | **Documented in R4** as `TRANSFER_PER_GENERATION = 8` |
| `getConfiguration()` | Only used by `ModConfigurationGui` | Keep, but it leaks the mutable Forge `Configuration` (C4) |
| `mInstance` public static | Used by `SolarPanelBlock.openGui` | Keep (standard 1.7.10), but consider `@Mod.Instance` + a getter |

Dead *tests*: `UpgradeItemTest` no longer spends two tests on `WaferSawItem` (deleted), and
`TraversalEnergyDispenserModule` now has a dedicated test class (batches M1/P2).

---

## 5. Feature proposals

Ordered by value ÷ effort for a 1.7.10 RF mod. All of these are small enough to land independently.

1. **WAILA/HWYLA provider** (`mcp.mobius.waila.api.IWailaDataProvider`). The mod already computes
   everything WAILA wants (stored, capacity, generation, sun, upgrades); the current alternative is
   `infoOnSneakClick`, which is off by default and prints to chat. Requires the split sync packet from
   §2.1 so the client has live values. High player value, ~150 lines, and it can be tested by calling
   the provider with a mocked accessor.
2. **Comparator output** (`Block.getComparatorInputOverride`). One method, returns
   `getScaledEnergyStoredFraction(15)`. Lets players build vanilla redstone automation around charge
   level. Trivially testable.
3. **In-place upgrade insertion.** Right-click a panel while holding an upgrade (not sneaking, panel
   has room) → insert one, play a sound, return `true`. Removes the GUI round trip. Needs the same
   "additionalUpgradeAllowed" guard as the container, so test both together.
4. **Config GUI coverage.** `ModConfigurationGui` shows only `CATEGORY_GENERAL`; add the upgrade and
   tier categories (or a tab per category) and a "restart required" note for geometry/tier options
   (C3). Also expose the tier defaults as read-only text so players can see the progression.
5. **`/solarflux` command.** `/solarflux info` for the panel the player is looking at (or the tile
   under their feet): tier, stored/capacity, generation, sun, upgrades, target list, tier config.
   `/solarflux reload` (with the C3 caveats). Support teams will use it; it is the cheapest possible
   integration test surface.
6. **Total energy generated statistic.** Track a `long mTotalGenerated` (NBT-saved, B stats-only) and
   show it in the GUI/tooltip ("Lifetime: 1.2 M RF"). Cheap, gives players a reason to keep panels
   and makes a great deterministic unit test (`generateEnergy` sums exactly).
7. **Redstone control.** A config toggle (or an upgrade) that pauses generation while the panel
   receives a redstone signal, plus `getWeakChanges`/`onNeighborChange` handling. Pairs naturally
   with the comparator output and is testable headlessly.
8. **CoFH hammer / IC2 wrench support.** `hasUsableWrench` only knows BuildCraft's `IToolWrench`.
   Add a safe reflective/interface check for `cofh.api.item.IToolHammer` (CoFHCore is already a
   required dependency) and optionally IC2's `IElectricItem`+wrench. This is the most common
   "how do I pick it up?" support question for 1.7.10 packs.
9. **Localisation pass.** Move "Crafting component" and "Solar Panel" into `en_US.lang`, and use
   `I18n.format` for the GUI title (G1/G2). Also add the missing keys for `ko_KR`, `pl_PL`, `ru_RU`
   (or at least make the fallback behaviour explicit — 1.7.10 returns the key, not English).
10. **Panel tier upgrade item** (metadata-based). The block's metadata is unused and there is one
    block class per tier; an "upgrade kit" that swaps a panel's tier in place (preserving energy and
    upgrades) would remove a dismantle/replace dance and give metadata a purpose. It would also
    finally clean up the `solar0` block name vs `solar1` registry name (R5) if the tier is moved into
    metadata. Larger change; do it after the test harness can cover it.
11. **OpenComputers/ComputerCraft peripheral** (stretch). `getEnergyStored`, `getMaxEnergyStored`,
    `getSunIntensity`, `getGeneration`, `getUpgrades` as read-only methods. ~100 lines plus an API
    dependency; valuable on tech servers.

---

## 6. Test coverage plan

### 6.1 Where the holes are

| Class / package | Line cov. | Why it is uncovered | Priority |
| --- | --- | --- | --- |
| `init.recipes` (3 classes) | **0 %** | Needs `GameRegistry.addRecipe`; only reachable in a Forge launch | P1 game test |
| `ThermalExpansionRecipeRegistrar` | 0 % | Needs TE + CoFH helper classes | P2 seam + game test |
| `TraversalEnergyDispenserModule` | **14 %** | No test file exists | **P0** |
| `ModConfiguration` | 20 % (unit) / 85 % (game) | `initialize()` needs FML | P1 seam |
| `core.tileentity` | 23 % | Only reached via TE tests | P2 |
| `SolarPanelBlock` | 50 % | Place/dismantle/tooltip paths untested | **P0** |
| `SolarPanelTileEntity` | 68 % | Sun/weather/upgrade maths untested | **P0** |
| `items.UpgradeItem.addInformation` | 21 % | Shift-held tooltip untested | P2 |
| `items.CraftingItem` | 26 % | Tooltip untested | P3 |
| `Utils.hasUsableWrench` | untested (non-null path) | Needs `ModAPIManager` | P2 (after R1) |
| `gui.ContainerSolarPanel` / `SlotUpgrade` | **excluded** | Excluded by `jacocoClassDirs()` | **P0** (I1–I3) |
| `core.gui.BaseModContainer` | excluded | Same | **P0** (I4) |
| `ModBlocks` / `ModItems` / `ModRecipes` | 0 % unit / 98 % game | Forge registries | Keep in game test |

### 6.2 Harness changes that unlock the tests

These are prerequisites; do them first.

* **`SolarFluxTestBase`** with a `@Before` that calls `applyDefaultConfiguration()`,
  `installUpgradeItems()` and `disableAutoBalance()`, and an `@After` that resets
  `mSolarPanelHeight`/`mConnectedTextures`. Today every test repeats the first three lines and a
  forgotten reset silently leaks into the next class in the same JVM.
* **`TestWorld` builder**: a `mock(World.class)` with `provider` set (needed by `EntityItem`), plus
  helpers for `getTileEntity(x,y,z)`, `canBlockSeeTheSky`, `getCelestialAngleRadians`,
  `isRaining`, `isThundering`, `getTotalWorldTime`, `getWorldTime`. The sun tests then read like a
  table instead of 20 lines of stubbing each.
* **Deterministic tick alignment** (T1): `mTickShift` is a final field filled from the global
  `Utils.RANDOM` in the constructor, so every `atTickRate` test loops to find a matching world time.
  Add a package-private `setTickShift(short)` (or make the field non-final and expose it through a
  `@VisibleForTesting` setter) and pin it in tests. This also makes the sun tests deterministic.
* **`TestPanel` builder**: `(tier, world, x, y, z, upgrades…)` one-liner.
* **Narrow the JaCoCo exclusion**: measure `gui.ContainerSolarPanel`, `gui.SlotUpgrade`,
  `core.gui.BaseModContainer` (they are plain Java, no LWJGL). Exclude only `GuiSolarPanel`,
  `core.gui.BaseModGui`, the two config-GUI classes and `creativetab`. This makes I1–I4 visible to the
  gate and stops them regressing.
* **Per-package JaCoCo rules** instead of one bundle-wide ratio. A 2,000-line uncovered class can
  currently hide behind the 46 % average. Suggested: `blocks` ≥ 65 %, `blocks.modules` ≥ 70 %,
  `core.*` ≥ 85 %, `items` ≥ 80 %, `config` ≥ 70 %, plus the existing overall floor.
* **Seed control for `Utils.RANDOM`**: make it a `protected static`/settable field so a test can
  reproduce a "random" failure.

### 6.3 New / extended unit tests

Grouped by file; the names are suggestions, the assertions are the point. The entries marked
**regression** encode the defects above; most of them fail on today's code and become the proof that
the fix holds.

#### `blocks/StatefulEnergyStorageTest` (extend; currently 100 % line, but the important paths are not)

1. **regression** `autoBalanceWithATransferSpeedNeverChangesTheTransferLimits` — 480,000,000 RF/t,
   `speed=100`, assert `getMaxTransferReceive/Extract` are unchanged and energy is conserved (E3).
2. **regression** `autoBalanceBetweenDifferentTiersNeverCreatesEnergy` — tier I rich (50,000 stored,
   transfer 16) balances with an empty tier VI (transfer 32,768), with and without `speed=100`; assert
   `rich + poor == 50,000` (E5; today it becomes 73,400 with the speed overload and 74,984 without).
3. `sendMaxToNeverCreatesEnergyFromAnOverReportingReceiver` — receiver returns more than it accepts;
   assert the panel's decrease equals the honest amount.
4. `energyStaysWithinCapacityAcrossRandomOperations` — 100k seeded `receive`/`extract`/`set`/`balance`
   operations; invariants `0 <= energy <= capacity`, return values equal the deltas.
5. `setMaxEnergyStoredClampsAndReportsTheLostEnergy` — pins the E4 decision (energy is
   capacity-clamped), whichever way the policy lands.

#### `blocks/SolarPanelTileEntityTest` (extend; currently 22 tests, 68 % line)

6. `aPanelAtNoonGeneratesTheTierMaximum` — `canBlockSeeTheSky=true`, celestial angle 0, no weather;
   assert `getCurrentEnergyGeneration() == max`.
7. `generationFollowsTheCelestialAngle` — angles 0, π/4, π/2, 3π/4, π → exact expected values (use
   the default multiplicator 1.5 and displacement 1.2); assert 0 at π and monotonic decrease after noon.
8. `anObstructedPanelGeneratesNothing` — `canBlockSeeTheSky=false`; also assert the *stored* value does
   not change across 40 ticks.
9. `rainAndThunderMultiplyTheGeneration` — rain ×0.4, thunder ×0.4, both ×0.16; assert the rounded
   integer exactly, and that clear weather restores the base value.
10. `efficiencyUpgradesFollowTheConfiguredReturnsToScale` — 0/1/4/8 upgrades with α=0.05, β=0.9;
    assert `round(gen * (1 + α·n^β))` for each.
11. `lowLightUpgradesTradeNoonOutputForAWiderDaylightWindow` — with 8 low-light upgrades, assert
    noon < base and that an angle near dusk is non-zero while base is zero.
12. **regression** `generationIsNotCappedByTheTransferRate` — set `maxTransfer` below the generation
    and assert the storage fills at the generation rate (E2).
13. **regression** `theClientDoesNotGenerateEnergy` — `world.isRemote=true`, generation > 0, call
    `updateEntity`, assert stored unchanged (E6).
14. **regression** `extractEnergyHonoursTheRequestedAmount` and
    `extractEnergyFromTheTopFaceReturnsZero` (E1).
15. `anUnknownTierIndexFallsBackToAValidTier` (C2) — construct with a tier index ≥ list size and
    assert a sane configuration rather than an exception.
16. `removingACapacityUpgradeClampsStoredEnergy` — install, fill to the upgraded max, remove, assert
    the new max and that no energy was created (E4).
17. `theUpgradeCacheIsRebuiltWhenTheTileEntityIsMarkedDirty` — already partly covered; add the
    "inventory changed without markDirty is not observed" case to document I8 after the fix.
18. `atTickRateIsDeterministicWithAPinnedShift` — after the T1 setter.

#### `blocks/modules/TraversalEnergyDispenserModuleTest` (new; **~15 tests**)

19. `aStraightLineOfReceiversIsDiscoveredBreadthFirst`
20. **regression** `theSameBlockIsNeverQueuedTwice` (M1 — the queue must be a set or checked)
21. `theSearchStopsAtTheConfiguredMaximumExtraTargets` — 1 upgrade → 1 extra, assert exactly
    `directNeighbours + 1` visited, not the whole grid.
22. `theTargetListIsOnlyReplacedWhenASearchCompletes` — assert `getTargets()` is unchanged while the
    queue is non-empty (M2 behaviour, documented).
23. `theSearchSurvivesAnEmptyNeighbourhood` — no `NoSuchElementException` from `LinkedList.remove`.
24. `theUpdateRateComesFromTraversalUpgradeUpdateRate` — 25 default, override to 1 and 100.
25. `aRemovedReceiverDisappearsAfterTheNextSearch` — flip the mock to `null`, complete a search,
    assert the target list shrinks.
26. `panelsAreNotTraversalTargets` — a neighbouring `SolarPanelTileEntity` is a provider, not a
    receiver; assert it is not discovered (but its own search still runs).
27. `noTraversalUpgradeStillVisitsTheDirectNeighbours` — `maxExtra=0`.

#### `gui/ContainerSolarPanelTest` (new; after narrowing the exclusion)

28. **regression** `shiftClickingAnUpgradeNeverDeletesOrDuplicates` — fill all five slots with other
    upgrade types, shift-click a 64-stack; assert total before == total after (I1).
29. `shiftClickingRespectsThePerPanelMaximum` — `additionalUpgradeAllowed` is the only count gate.
30. `shiftClickMovesBetweenPlayerAndPanelInventories` — both directions, including a full inventory.
31. `progressBarsRoundTripStoredEnergyAndGeneration` — pack/unpack 0, 1, 0xFFFF, 0x10000,
    Integer.MAX_VALUE; extract the bit-packing into a `EnergySync` helper so this needs no container.
32. **regression** `draggingIsAllowedOnEveryPlayerSlotAndBlockedOnUpgradeSlots` — container indices
    0–4 false, 5–40 true (I2). This single test catches the hotbar collision.
33. `slotClickNeverLosesTheCursorRemainder` — a custom `UpgradeItem` with `max=256` (allowed by the
    config) and a full target slot (I3).

#### `core/gui/BaseModContainerTest` (new)

34. `mergeItemStackHonoursTheItemMaxStackSize` — stackable item with max 1/16 into an empty slot (I4).
35. `mergeItemStackMergesIntoExistingStacksBeforeEmptySlots` (documents the override).
36. `mergeItemStackNeverExceedsTheSlotStackLimit`.

#### `core/inventory/BaseInventoryTest` (extend)

37. **regression** `outOfRangeSlotIndexesAreDroppedInsteadOfThrowing` — replace the current
    `@Test(expected = ArrayIndexOutOfBoundsException.class)` with the fixed behaviour (I6).
38. `inventoryRoundTripForEverySize` — sizes 1..8, 0..64 stacks, random contents (property).

#### `config/ModConfigurationTest` (new; needs the `ConfigSource` seam)

39. **regression** `disabledTiersDoNotTruncateTheList` — map-backed config with tier 1 inactive and
    tiers 2–5 active; assert the tier identities/values are preserved or an explicit warning is
    logged (C1).
40. `derivedTiersBeyondTheDefaultsDoubleGenerationAndTransferAndGrowCapacityBy1_2`.
41. `getTierConfigurationClampsOutOfRangeIndexes` (C2).
42. `everyAccessorReadsItsOwnField` — extends the existing `ModConfigurationSurfaceTest`, but through
    the loader so the wiring is exercised.
43. `configurationReloadOnlyAppliesRestartSafeValues` — documents C3 (block bounds/icon handler are
    not re-applied).

#### `blocks/SolarPanelBlockTest` (extend; currently 50 %)

44. `placingAPanelRestoresEnergyAndUpgradesFromTheItem` — build an NBT item (energy 1234 + 2 efficiency),
    call `onBlockPlacedBy`, assert the TE. This is the mirror of the existing "wrenched panel keeps
    its charge" test and currently absent.
45. `placingAPanelWithACorruptInventoryDropsTheExtraSlots` (after I6).
46. `dismantlingKeepsTheEnergyAndInventoryOnlyWhenConfigured` — the four combinations of
    `KeepEnergyWhenDismantled` × `KeepInventoryWhenDismantled`, asserting the drop NBT and that no
    inventory is duplicated when the TE is removed.
47. `sneakClickInformationIsOffByDefaultAndOnWhenConfigured` — capture the chat message; assert the
    formatted numbers.
48. `onBlockActivatedReturnsTrueOnBothSides` (already partly) and `aWrenchClickDoesNotOpenTheGui`.

#### `items/UpgradeItemTest` + `CraftingItemTest` (extend)

49. `theTooltipShowsTheShiftHintWhenShiftIsNotHeld` and `showsTheUpgradeDetailsWhenHeld` — stub
    `Utils.isShiftKeyDown` (after moving it, or use `PowerMock`-free `Mockito.mockStatic` — Mockito
    4.11 supports `mockStatic`, already on the classpath via `mockito-inline`).
50. `theCraftingItemTooltipIsLocalised` (after G2).

#### `core/utility/WrenchHelperTest` (new, after R1)

51. `aBuildCraftWrenchCanDismantle`, `aCoFHHammerCanDismantle`, `aRandomItemCannot`,
    `aPlayerWithNoItemCannot`, `aWorldInteractionIsNotUsedOnTheClient`.

#### Localisation/data integrity (new, plain unit test reading resources from the classpath)

52. `everyLangFileUsesTheSameFormatSpecifiersAsEnglish` — de_DE/fr_FR/pt_BR/zh_CN currently match;
    this pins it (a `%d` where the code passes a float throws at runtime).
53. `everyKeyUsedInCodeExistsInEnUS` — assert the `info.solarflux.*` keys used by `Lang.localise`
    and the `tile/item` keys for the six tiers.
54. `everyTierTextureExists` — check `assets/solarflux/textures/blocks/solarN_0..15.png` for every
    `DEFAULT_TIER_CONFIGURATIONS` index; catches a future tier-7 texture that ModBlocks would
    silently alias to tier 6.

### 6.4 Game-test additions (`src/gameTest`)

These cover the parts a unit test genuinely cannot. The current suite is good; the gaps are the
behaviours that were just found:

55. **Sun curve in situ**: at midnight (18000) assert `generation == 0` and no energy is gained over
    N ticks; at noon assert `generation == max`; then set rain and assert ×0.4 (and thunder ×0.4).
    This is the strongest possible regression test for `computeSunIntensity` and does not need mocks.
56. **Upgrades in a real panel**: place a panel, insert 2 capacity + 2 transfer upgrades through the
    *inventory* (not the container), call `markDirty`, assert capacity/transfer; remove them and
    assert restoration (covers the cache refresh path in-game).
57. **Traversal chain**: panel at (0,0,0), receivers at (1,0,0), (2,0,0), (3,0,0); a traversal
    upgrade must discover all three within a fixed number of ticks, and energy must arrive at the far
    end. This is the only end-to-end coverage of M1/M2.
58. **Energy sharing**: two adjacent panels, one full and one empty; after 100 ticks assert they are
    balanced and the total is unchanged (energy conservation in-game).
59. **Dismantle → place round trip**: give the panel energy + 2 upgrades, wrench it, assert the drop's
    NBT; place the drop, assert the TE's energy and upgrade count. The item `readFromNBT` path
    (registered items) is only reachable in-game.
60. **Furnace upgrade**: place a vanilla furnace next to the panel, add the furnace upgrade, put coal
    in slot 0, assert `furnaceBurnTime` increases and the coal is not consumed (the module writes
    `furnaceBurnTime` directly).
61. **Recipe content, not just presence**: assert `ShapedOreRecipe`s for tier 1..6 include the
    previous tier and the expected cell; assert the mirror recipe accepts iron and, when present,
    tin/aluminium. Today only "a recipe exists" is checked.
62. **Registry/name checks**: assert the block item is a `SolarPanelItemBlock`, that
    `GameRegistry.findItem(MOD_ID, "solar1")` matches the block, and that `mcmod.info`'s version
    matches `Tags.VERSION`.
63. **Config surface**: assert every configured tier has a `tile.solarflux:solarN.name` entry by
    reading the lang resource from the jar (server-side `StatCollector` is not reliable for this).
64. **Client/server split**: assert `getDescriptionPacket()` contains only the sync keys (after §2.1)
    and not the inventory — this prevents a future regression that ships 5 item stacks per panel per
    chunk load.

### 6.5 CI / build harness

* `game-test.yml` runs `./gradlew test gameTestServer`; keep it, but also run `check` (or
  `spotlessCheck checkstyleMain`) so style drift cannot land through the game-test path. The
  reusable `build-and-test.yml` already runs `check`, but the explicit workflow is what developers
  copy.
* The client half (`./gradlew runClient -PgameTest`) is fully automated — it exits with the result — but
  it needs a display. On CI that means `xvfb-run` plus a software GL stack (Mesa/llvmpipe); it is not
  wired into `game-test.yml` yet because that setup is unverified here. Run it locally or on a
  self-hosted runner with a GPU.
* Raise the JaCoCo floor. Measured baseline is 52.3/59.9 with a 46/50 gate; set the floor to the
  measured value minus ~1 point, and add the per-package rules from §6.2 so a single huge uncovered
  class cannot hide.
* Add `jacocoTestReport` as a dependency of `check` (currently only `jacocoTestCoverageVerification`
  is wired), so the HTML is always fresh after `./gradlew check`.
* Consider `diff-cover` (or a `jacocoTestCoverageVerification` rule on new classes) in PR CI: the
  bundle-wide ratio does not protect a new class. This is a small workflow step, not a Gradle plugin.
* `Test` logging: keep `failed`/`skipped` locally, but add `--info`/`showStandardStreams` on the CI
  job so the game-test output and any `System.out` diagnostics are visible in the log artifact.
* The game-test workflow uploads `run/server/logs/` and `build/reports/jacoco/`; also upload
  `build/reports/tests/test/` on failure. Cheap and it saves a round trip.
* Keep the game-test coverage report separate (the README explains why the class ids cannot be
  merged; that is correct). Consider publishing the game-test percentage as an informational metric
  in the job summary so regressions are visible even though they are not gated.

### 6.6 Suggested order and expected outcome

| Wave | Work | Tests added | Coverage effect |
| --- | --- | --- | --- |
| 0 | Harness: `SolarFluxTestBase`, `TestWorld`, pinned tick shift, narrow the exclusion | — | unlocks 1–3 |
| 1 | Regression tests for E1/E2/E3/E5/E6/I1/I2/M1/C1/C2/I6 (the bugs above) | ~20 | `StatefulEnergyStorage`, `SolarPanelTileEntity`, `ContainerSolarPanel`, `Traversal` |
| 2 | Sun/upgrade/weather table tests + `TraversalEnergyDispenserModuleTest` | ~20 | `blocks` ≈ 85 %, `blocks.modules` ≈ 85 % |
| 3 | Container/`mergeItemStack` + `BaseModTileEntity(Synced)` + `GuiHandler` + `Utils` | ~15 | removes the `gui` blind spot |
| 4 | `ConfigSource` seam + `ModConfigurationTest` + recipe seam | ~12 | `config` ≈ 80 %, `init.recipes` > 0 headless |
| 5 | Game-test additions 55–64 | ~20 checks | game-test lines/branches up, no unit impact |

Expect the filtered unit coverage to land around **65–70 % line / 65 % branch** after waves 1–3,
which is a defensible place to raise the floor. The remaining 30 % is the recipe registrars, the
registration classes, the mod entry point and the rendering/config GUI code — all of which need a
Forge launch or a booted client, and the game test is the right home for them.

---

## 7. Quick wins (independent, under an hour each)

Done: 1 (E1), 2 (E5), 3 (E3), 4 (I1 and I2), 6 (M1), 7 (I6), 8 (C2), 11 (E2). Batches 3–4 also
cover E4, E6, I3, I8 and M3, which were not on this list. Open: the rest.

1. `extractEnergy`: honour `pMaxExtract` and `pFrom` (E1) + update the pinned test. **Done.**
2. `autoBalanceEnergy`: compute the move against both caps so mixed tiers stop duplicating RF (E5) +
   the regression test — this is the one to ship first. **Done.**
3. `autoBalanceEnergy(other, speed)`: stop mutating the int limits / clamp with `long` (E3) + test.
   **Done.**
4. `ContainerSolarPanel`: always add the split stack back (I1) and fix `canDragIntoSlot` (I2). **Done.**5. `GuiSolarPanel`: use the panel's inventory name (G1).
6. `TraversalEnergyDispenserModule`: use a `LinkedHashSet` for the queue and check membership (M1) +
   the new test class. **Done** (linked list + membership check).
7. `BaseInventory.readFromNBT`: bounds-check the slot index (I6) and replace the pinned AIOOBE test.
   **Done.**
8. `ModConfiguration.getTierConfiguration`: clamp + log (C2). **Done.**
9. `ModBlocks`: keep a hard 16/32-tier cap and log when a tier is disabled above an enabled one (C1).
10. Delete `WaferSawItem` (or finish it), `MetricUnits.TERA`, unused `Color` constants,
    `BlockPosition.forward()` and the unused `LogHelper` methods (§4).
11. `statefulEnergyStorage.addEnergy` + `generateEnergy` (E2) + test. **Done.**

---

## Appendix A — reproduction notes

**E3 (overflow).** Ran a throw-away JUnit test in the `blocks.modules` package (deleted afterwards):

```java
StatefulEnergyStorage a = new StatefulEnergyStorage(1_000_000, 480_000_000);
StatefulEnergyStorage b = new StatefulEnergyStorage(1_000_000, 480_000_000);
a.setEnergyStored(1_000_000);
a.autoBalanceEnergy(b, 100);
// before: a.extract = 480000000
// after:  a.extract = 7553597, b.extract = 7553597
// energy: a = 500000, b = 500000 (conserved here, but the limits are destroyed)
```

The threshold is `transfer * speed > Integer.MAX_VALUE`, i.e. **21,474,836 RF/t** at speed 100. The
shipped tiers (transfer = 8 × generation = 32,768 max) do not reach it; a pack config with
`MaximumEnergyTransfer` above 21.4 M does, and the config explicitly allows up to 2,000,000,000.
After the overflow, `getMaxExtract()` is 7,553,597, so the panel's output is silently capped. For
other magnitudes the wrapped value is negative (e.g. 30,000,000 × 100), and then `receiveEnergy`
*subtracts* from the stored energy — driving it below zero — while `extractEnergy` adds to it.

**E5 (mixed-tier duplication).** Throw-away JUnit test (deleted afterwards), default tier rates,
speed 100 (the value `EnergySharingModule` uses):

```
StatefulEnergyStorage richTier0 = new StatefulEnergyStorage(50_000, 16);
StatefulEnergyStorage poorTier5 = new StatefulEnergyStorage(60_000_000, 32_768);
richTier0.setEnergyStored(50_000);
richTier0.autoBalanceEnergy(poorTier5, 100);
// before: rich=50000  poor=0
// after:  rich=48400  poor=25000   total=73400  (+23,400)

// same tier, for contrast:
StatefulEnergyStorage a = new StatefulEnergyStorage(50_000, 16);
StatefulEnergyStorage b = new StatefulEnergyStorage(50_000, 16);
a.setEnergyStored(50_000);
a.autoBalanceEnergy(b, 100);
// after: a=48400 b=1600 total=50000 (no creation)
```

The duplication is `receiveEnergy` succeeding on the receiver's larger cap while `extractEnergy` is
clamped by the sender's smaller cap; it is not related to the E3 overflow.

**M1 (traversal duplicate).** Throw-away test with three receivers arranged as a diamond around the
panel ((1,0,0), (0,0,-1), (1,0,-1)); after completing a search, `visited` contained all three while
the queue still held `(1,0,-1)`, which had already been visited (discovered from both parents). The
`mDirectNeighborDiscovered` counter was 2 (queue length at search start), so duplicates inflate the
base used by the `MaximumExtraTargets` cap.

## Appendix B — checked and *not* bugs

* **`setLightOpacity(255)` on a partial block** matches vanilla `BlockSlab` (line 35 of
  `BlockSlab.java`). `useNeighborBrightness = true` is the right companion; no change needed.
* **`canConnectEnergy(UP) == false`** is deliberate (the top is the solar face) and is pinned by
  `energyCanBeExtractedFromEverySideButTheTop`. It only needs the E1 enforcement.
* **The `solar0` block name vs `solar1` registry name** is intentional and consistent with the lang
  files; the game test pins it. It is merely confusing, not broken.
* **`getIcon(int side, …)` returning the side icon for the inventory render** is fine: the item
  renderer asks for all faces, and `TopIconHandler` routes face 1 to the top icon.
* **`Utils.RANDOM` being a global `Random`** is fine for gameplay; it is only a test-determinism
  problem (T1).
* **`EnergySharingModule` only checking +X and +Z** is a deliberate half-neighbourhood optimisation
  (each panel balances its own pair); convergence is slower but not wrong once E3/E5 are fixed.
* **`ModBlocks.getSolarPanels()` returning a list** is the only way the recipe code can enumerate
  tiers in 1.7.10; making it unmodifiable is enough (C4).
* **The coverage exclusion for `GuiSolarPanel`/config GUI** is justified (needs a booted client); the
  problem is that it also excludes the container logic (§6.2).
