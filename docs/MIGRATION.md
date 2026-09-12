# Migration and rollback

TwiBoxCore replaces these two runtime plugins:

- `TwilightEquipmentEffectsGuard`
- `TwilightLegacyCombatCompat`

Do not keep either old JAR beside TwiBoxCore. Duplicate listeners would apply the same mechanics twice.

## Preflight

1. Confirm the server runs Java 21+ and a Paper-compatible 1.21.11 build.
2. Record hashes of the old JARs and back up their configuration directories. `TwilightEquipmentEffectsGuard` has no configuration directory; `TwilightLegacyCombatCompat/config.yml` is the recognized migration source.
3. Back up player data because both modules can normalize matching items.
4. Confirm there are no players online and stop the server cleanly.

## Migration

1. Move the two old JARs outside `plugins/`; keep them for rollback. Leave the old `TwilightLegacyCombatCompat/config.yml` available through the first TwiBoxCore start.
2. Install the TwiBoxCore release JAR.
3. Start the server once. The importer reads only seven recognized keys, writes a hash-named source backup under `plugins/TwiBoxCore/migration-backups/`, and records completion in `plugins/TwiBoxCore/migration-state.yml`.
4. Review `plugins/TwiBoxCore/config.yml` before opening the server to players. Existing non-default TwiBoxCore values are preserved; unknown source keys are ignored.
5. Confirm `EQUIPMENT_READY`, `COMBAT_READY`, and the final enable message appear once.
6. Run `/twiboxcore status` and verify `legacyImport=completed`, then run `/twiboxcore selftest`.
7. Restart the staging copy once more and confirm the state remains `completed` and neither config nor backup changes. This is the idempotence check.
8. Test each restricted block with its intended tool and with an invalid tool.
9. Test a rapid hotbar/off-hand swap; the restricted block must not break using carried progress.
10. Test representative legacy armor and weapon items, including an item that must remain untouched.

The defaults preserve the old PDC keys and mechanics. The legacy commands and permissions remain available.

## Negative checks

- No duplicate old plugin is enabled.
- No unrelated inventory item changes during a scan.
- Creative-mode block handling remains unrestricted.
- Damage reduction never exceeds the configured cap.
- An invalid material name is rejected with a warning instead of matching arbitrary blocks.
- A missing or malformed source produces no completion marker, so a corrected source can be retried.
- The importer never deletes or edits the legacy source and never scans unrelated plugin directories.

## Rollback

1. Stop the server cleanly.
2. Remove TwiBoxCore.
3. Restore the two old JARs and their configuration backups. The source file remains in place unless an operator moved it manually.
4. If item normalization itself must be reverted, restore the preflight player-data backup.
5. Start the server and repeat the representative tool and combat tests.

Never hot-load or hot-unload these listeners on a production server.
