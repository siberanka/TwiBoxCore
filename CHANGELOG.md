# Changelog

All notable changes are documented here.

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
