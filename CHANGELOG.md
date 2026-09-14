# Changelog

All notable changes are documented here.

## 1.6.0 - 2026-09-14

- Replaced the reflective Skript warp suggestion generator with a bounded native Paper event filter.
- Kept EssentialsX permissions authoritative and removed only configured internal first-argument warps.
- Preserved rich asynchronous completion objects and tooltips without marking requests handled or cancelled.
- Added immutable exact-match filtering, malformed-config rejection, alias coverage, and positive/negative tests.

## 1.5.0 - 2026-09-14

- Integrated the complete entity-free FancyNpcs Shopkeepers object lifecycle.
- Preserved existing `fancynpc` shop data, optional `npcId` values, virtual spawn IDs, and delayed NPC equipment refreshes.
- Added a guarded sneak-right-click administrator editor using the Shopkeepers API directly.
- Added strict UUID action parsing, bidirectional NPC identity validation, all-of permission checks, distance and cooldown bounds, main-thread revalidation, and injection-focused negative tests.
- Added hard startup dependencies so saved custom shop objects are registered before deserialization.

## 1.4.0 - 2026-09-14

- Added a throttled action-bar warning when restricted mine blocks are struck with the wrong tool.
- Preserved the exact live display-name colors and bold state for the Witch Shears, Cyber Shears, Zeus Hoe, and Glacier Pickaxe.
- Kept arm-delay and carried-progress rejections silent so valid tools never receive a misleading wrong-tool warning.

## 1.3.0 - 2026-09-13

- Added separate persistent identities for the End and Siber shears.
- Restricted each shears variant to its own world and wool palette.
- Kept both shears at the same configured final break-speed multiplier.
- Extended runtime self-tests to cover cross-mine identity separation.

## 1.2.0 - 2026-09-13

- Merged strict Shopkeepers-canonical equipment synchronization into TwiBoxCore.
- Removed gameplay PDC bookkeeping from repaired items.
- Removed periodic full-inventory scans; synchronization is administrator-triggered.
- Added bounded canonical refresh/export commands for audited offline migrations.
- Preserved item amount, enchantment data, and enchantment tooltip visibility.
- Updated special-tool final speed multipliers to 0.60, 0.30, and 0.15.

## 1.1.0 - 2026-09-12

- Added a bounded one-time importer for `TwilightLegacyCombatCompat/config.yml`.
- Added hash-named source backups, atomic config/state writes, and a persistent completion marker.
- Preserved explicit non-default TwiBoxCore values and ignored unknown legacy keys.
- Exposed migration state through `/twiboxcore status`.
- Documented that the equipment-effects predecessor had no config to import and that UnlimitedNameTags remains outside the project.
- Added migration mapping and idempotence tests.

## 1.0.0 - 2026-09-12

- Merged TwilightEquipmentEffectsGuard 1.5.0 behavior into the equipment-effects module.
- Merged TwilightLegacyCombatCompat 1.0.0 behavior into the legacy-combat module.
- Preserved legacy commands, permission aliases, ItemTag PDC data, and Twilight tool identifiers.
- Added module configuration, status reporting, reload support, runtime self-tests, and unit tests.
- Added production migration, rollback, and security documentation.
