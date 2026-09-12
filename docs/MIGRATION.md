# Migration and rollback

TwiBoxCore replaces these two runtime plugins:

- `TwilightEquipmentEffectsGuard`
- `TwilightLegacyCombatCompat`

Do not keep either old JAR beside TwiBoxCore. Duplicate listeners would apply the same mechanics twice.

## Preflight

1. Confirm the server runs Java 21+ and a Paper-compatible 1.21.11 build.
2. Record hashes of the old JARs and back up their configuration directories.
3. Back up player data because both modules can normalize matching items.
4. Confirm there are no players online and stop the server cleanly.

## Migration

1. Move the two old JARs outside `plugins/`; keep them for rollback.
2. Install the TwiBoxCore release JAR.
3. Review the generated `plugins/TwiBoxCore/config.yml` before opening the server to players.
4. Start the server and confirm `EQUIPMENT_READY`, `COMBAT_READY`, and the final enable message appear once.
5. Run `/twiboxcore status` and `/twiboxcore selftest`.
6. Test each restricted block with its intended tool and with an invalid tool.
7. Test a rapid hotbar/off-hand swap; the restricted block must not break using carried progress.
8. Test representative legacy armor and weapon items, including an item that must remain untouched.

The defaults preserve the old PDC keys and mechanics. The legacy commands and permissions remain available.

## Negative checks

- No duplicate old plugin is enabled.
- No unrelated inventory item changes during a scan.
- Creative-mode block handling remains unrestricted.
- Damage reduction never exceeds the configured cap.
- An invalid material name is rejected with a warning instead of matching arbitrary blocks.

## Rollback

1. Stop the server cleanly.
2. Remove TwiBoxCore.
3. Restore the two old JARs and their configuration backups.
4. If item normalization itself must be reverted, restore the preflight player-data backup.
5. Start the server and repeat the representative tool and combat tests.

Never hot-load or hot-unload these listeners on a production server.
