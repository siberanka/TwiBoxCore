# TwiBoxCore

[![Java 21+](https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Paper 1.21.11](https://img.shields.io/badge/Paper-1.21.11-222222)](https://papermc.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

TwiBoxCore is a focused Paper/Leaf plugin for restricted mining tools and high-level equipment compatibility on modern BoxPVP servers.

## Features

### Restricted mining tools

- Assigns persistent identities to special shears, hoes, and pickaxes.
- Restricts configured blocks by world, material, and required tool.
- Prevents hotbar, off-hand, and carried-progress bypasses.
- Applies configurable Mining Fatigue and per-tool break-speed multipliers.
- Shows a rate-limited action-bar hint with the required tool's configured display name.
- Repairs recognized older tool stacks without continuously scanning every inventory.

### Equipment compatibility

- Repairs missing native weapon and tool attributes.
- Rebuilds armor, toughness, and knockback-resistance values when old item data suppressed them.
- Adds capped, proportional damage reduction for protection levels above the vanilla range.
- Builds a canonical equipment catalog from Shopkeepers when that integration is available.
- Synchronizes matching online inventory items only through an explicit administrator command.

TwiBoxCore opens no network sockets, makes no outbound requests, and contains no telemetry, updater, webhook, licensing check, credentials, server address, or player data.

## Requirements

- Java 21 or newer
- Paper or Leaf 1.21.11
- Shopkeepers 2.27.x only when canonical catalog synchronization is needed

Dependencies are provided by the server and are not bundled into the release JAR.

## Installation

1. Stop the server cleanly and back up `plugins/` plus player data.
2. Copy `TwiBoxCore-1.4.0.jar` into `plugins/`.
3. Start the server and review `plugins/TwiBoxCore/config.yml`.
4. Run `/twiboxcore status` and `/twiboxcore selftest`.
5. Test every configured restricted block once with its intended tool and once with an invalid tool before opening the server to players.

Do not hot-load or hot-unload the plugin on a production server. See the [migration and rollback guide](docs/MIGRATION.md) when replacing an earlier installation.

## Configuration

The bundled [`config.yml`](src/main/resources/config.yml) controls:

- enabled modules;
- fatigue level, duration, and hand-switch delay;
- wrong-tool action-bar text, tool names, and cooldown;
- final break-speed multiplier for each tool;
- restricted worlds and block materials;
- high-level armor repair and protection scaling.

Break-speed values are final multipliers: `0.40` means 40% of the otherwise calculated speed. Bukkit potion amplifiers are zero-based, so `1` means Mining Fatigue II.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/twiboxcore status` | `twiboxcore.admin` | Show module and catalog status |
| `/twiboxcore reload` | `twiboxcore.admin` | Reload configuration and modules |
| `/twiboxcore scan` | `twiboxcore.admin` | Synchronize matching online inventory items |
| `/twiboxcore refresh` | `twiboxcore.admin` | Rebuild the canonical equipment catalog |
| `/twiboxcore export` | `twiboxcore.admin` | Export the catalog for an audited offline migration |
| `/twiboxcore selftest` | `twiboxcore.admin` | Run runtime compatibility checks |
| `/twilighttool give buzul <player>` | `twilighttool.admin` | Issue the canonical glacier pickaxe |
| `/twilightcombat <status\|scan\|refresh\|export\|selftest>` | `twilightcombat.admin` | Compatibility command alias |

Administrative permissions default to server operators.

## Build

```shell
mvn --batch-mode clean verify
```

The verified artifact is written to `target/TwiBoxCore-1.4.0.jar`. The project intentionally contains no CI/CD workflow.

## Documentation

- [Changelog](CHANGELOG.md)
- [Migration and rollback](docs/MIGRATION.md)
- [Security policy](SECURITY.md)
- [Third-party boundaries](THIRD_PARTY.md)

## License

TwiBoxCore is released under the [MIT License](LICENSE).
