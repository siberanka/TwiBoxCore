# Merge provenance

Version 1.0.0 consolidates two independently deployed, project-owned runtime plugins:

| Original component | Original version | TwiBoxCore module |
|---|---:|---|
| TwilightEquipmentEffectsGuard | 1.5.0 | `equipment-effects` |
| TwilightLegacyCombatCompat | 1.0.0 | `legacy-combat` |

The merge preserves their mechanical constants, legacy commands, permission aliases, and persistent-data namespaces. Runtime settings are now visible in one versioned configuration file.

The temporary `TwilightFatigueMigrator` administration utility is intentionally excluded. Its one-shot purpose is superseded by the equipment module's continuous, targeted normalization, and its historical effect level does not represent the current mechanics.

ItemEdit and ItemTag were audited as unmodified upstream artifacts during the merge and therefore remain external dependencies. UnlimitedNameTags is an unrelated third-party integration and is outside this repository's scope.
