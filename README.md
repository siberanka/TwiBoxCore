# TwiBoxCore

[![Java 21+](https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Paper 1.21.11](https://img.shields.io/badge/Paper-1.21.11-222222)](https://papermc.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

TwiBoxCore combines two focused BoxPVP compatibility plugins into one maintained Paper/Leaf plugin:

- special mining-tool enforcement and hotbar/off-hand bypass protection;
- legacy high-level armor repair and proportional protection scaling.

The merged plugin preserves the legacy commands and persistent-data keys, so existing special items remain recognizable. It contains no telemetry, update checker, webhook, licensing call, server address, credential, or player data.

Version 1.2 adds strict Shopkeepers-canonical item synchronization and removes
periodic full-inventory scanning. Synchronization is an explicit administrator
operation, and the legacy configuration importer remains bounded and one-time.
The equipment-effects predecessor did not have a configuration directory, so
there is no second config to import.

## Requirements

- Java 21 or newer
- Paper-compatible server 1.21.11
- ItemEdit and ItemTag are optional, soft dependencies. TwiBoxCore understands the existing ItemTag PDC representation but does not bundle or replace either project.

See [Third-party boundaries](THIRD_PARTY.md) for the dependency and licensing boundary.

## Installation

1. Stop the server and back up the plugin directory plus player data.
2. Remove `TwilightEquipmentEffectsGuard` and `TwilightLegacyCombatCompat` from the plugin directory. Do not run them together with TwiBoxCore.
3. Copy `TwiBoxCore-1.2.0.jar` into `plugins/`. Keep the old `TwilightLegacyCombatCompat/config.yml` in place for the first start if it should be imported.
4. Start the server and run `/twiboxcore status`, then `/twiboxcore selftest`.
5. Confirm `legacyImport=completed`; archived input and import state are stored under `plugins/TwiBoxCore/`.

For a production migration and rollback procedure, read [Migration guide](docs/MIGRATION.md).

## Modules

### Equipment effects

- Keeps legacy `itemtag:effects_list`, `itemtag:effects_equips`, `twilightnw:special_tool`, and break-speed data compatible.
- Normalizes the special shears, hoe, and glacier pickaxe in inventories and ender chests.
- Applies the configured Mining Fatigue level and tool-specific block-break multipliers.
- Rejects switching-hand, switching-slot, and carried-progress bypasses on configured restricted blocks.
- Hides the unbreakable tooltip without removing the underlying durability protection.

### Legacy combat

- Removes obsolete zero-value dummy gravity attributes left by old item serializers.
- Restores native weapon/tool attributes when an empty explicit attribute component suppressed them.
- Rebuilds armor, toughness, and knockback-resistance attributes for configured high-level protection armor.
- Adds capped, monotonic damage reduction above the vanilla total-protection threshold.
- Loads exact equipment templates from the Shopkeepers API.
- Synchronizes online inventories only when an administrator runs the scan command.
- Does not persist bookkeeping PDC on gameplay items and does not schedule a periodic full-inventory scan.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/twiboxcore status` | `twiboxcore.admin` | Show enabled modules and counters |
| `/twiboxcore reload` | `twiboxcore.admin` | Reload both modules safely |
| `/twiboxcore scan` | `twiboxcore.admin` | Scan online inventories for legacy combat items |
| `/twiboxcore refresh` | `twiboxcore.admin` | Refresh the in-memory Shopkeepers catalog |
| `/twiboxcore export` | `twiboxcore.admin` | Export the catalog for an audited offline migration |
| `/twiboxcore selftest` | `twiboxcore.admin` | Run runtime compatibility checks |
| `/twilighttool give buzul <player>` | `twilighttool.admin` | Legacy-compatible special tool command |
| `/twilightcombat <status\|scan\|selftest>` | `twilightcombat.admin` | Legacy-compatible combat command |

All permissions default to server operators only.

## Configuration

The bundled [`config.yml`](src/main/resources/config.yml) reproduces the original module behavior. It also makes world names, restricted materials, fatigue timing, speed multipliers, scan timing, and protection scaling explicit.

Break-speed values are final multipliers: `0.40` means 40% of the otherwise calculated speed. Bukkit potion amplifiers are zero-based: `1` means Mining Fatigue II.

On first start, `migration.import-legacy-configs: true` imports only the six recognized combat settings from `plugins/TwilightLegacyCombatCompat/config.yml`. Existing non-default TwiBoxCore values win, unknown keys are ignored, the source is never modified, and a hash-named backup plus completion record prevents repeated imports. See the [migration guide](docs/MIGRATION.md).

## Building

```shell
mvn --batch-mode clean verify
```

The verified artifact is written to `target/TwiBoxCore-1.2.0.jar`. The project deliberately has no CI workflow; releases are built and tested explicitly.

## Scope

TwiBoxCore does not contain ItemEdit, ItemTag, UnlimitedNameTags, server configurations, stored items, or world data. UnlimitedNameTags is an unmodified third-party plugin and is explicitly outside this merge. Operational deployment artifacts remain separate.

## License

TwiBoxCore is available under the [MIT License](LICENSE).
