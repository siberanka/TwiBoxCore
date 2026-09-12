package net.twilightnw.twiboxcore.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

final class LegacyCombatConfigMapping {
    private static final List<KeyMapping> KEYS = List.of(
            key("repair.enabled", "legacy-combat.repair.enabled"),
            key("repair.minimum-protection-level", "legacy-combat.repair.minimum-protection-level"),
            key("repair.online-scan-interval-ticks", "legacy-combat.repair.online-scan-interval-ticks"),
            key("protection-scaling.enabled", "legacy-combat.protection-scaling.enabled"),
            key("protection-scaling.vanilla-total-level-cap",
                    "legacy-combat.protection-scaling.vanilla-total-level-cap"),
            key("protection-scaling.extra-reduction-per-excess-level",
                    "legacy-combat.protection-scaling.extra-reduction-per-excess-level"),
            key("protection-scaling.maximum-extra-reduction",
                    "legacy-combat.protection-scaling.maximum-extra-reduction"));

    private LegacyCombatConfigMapping() {
    }

    static MappingResult apply(ConfigurationSection source,
                               ConfigurationSection target,
                               ConfigurationSection defaults) {
        Map<String, Object> explicitTargetValues = target.getValues(true);
        List<String> imported = new ArrayList<>();
        List<String> preserved = new ArrayList<>();
        boolean changed = false;
        for (KeyMapping mapping : KEYS) {
            if (!source.contains(mapping.sourcePath(), true)) {
                continue;
            }
            Object sourceValue = source.get(mapping.sourcePath());
            Object currentValue = target.get(mapping.targetPath());
            Object defaultValue = defaults.get(mapping.targetPath());
            if (Objects.equals(currentValue, sourceValue)) {
                imported.add(mapping.targetPath());
                continue;
            }
            boolean explicitlyConfigured = explicitTargetValues.containsKey(mapping.targetPath());
            if (!explicitlyConfigured || Objects.equals(currentValue, defaultValue)) {
                target.set(mapping.targetPath(), sourceValue);
                imported.add(mapping.targetPath());
                changed = true;
            } else {
                preserved.add(mapping.targetPath());
            }
        }
        return new MappingResult(List.copyOf(imported), List.copyOf(preserved), changed);
    }

    private static KeyMapping key(String source, String target) {
        return new KeyMapping(source, target);
    }

    record MappingResult(List<String> imported, List<String> preserved, boolean changed) {
    }

    private record KeyMapping(String sourcePath, String targetPath) {
    }
}
