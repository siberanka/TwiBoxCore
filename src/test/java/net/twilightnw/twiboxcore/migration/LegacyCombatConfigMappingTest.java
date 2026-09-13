package net.twilightnw.twiboxcore.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LegacyCombatConfigMappingTest {
    @Test
    void importsKnownKeysButPreservesCustomCoreValues() {
        YamlConfiguration defaults = bundledDefaults();
        YamlConfiguration target = bundledDefaults();
        YamlConfiguration source = legacyConfiguration();
        target.set("legacy-combat.repair.minimum-protection-level", 77);

        LegacyCombatConfigMapping.MappingResult result =
                LegacyCombatConfigMapping.apply(source, target, defaults);

        assertTrue(result.changed());
        assertEquals(77, target.getInt("legacy-combat.repair.minimum-protection-level"));
        assertFalse(target.getBoolean("legacy-combat.repair.enabled"));
        assertFalse(target.contains("legacy-combat.repair.online-scan-interval-ticks", true));
        assertEquals(0.004, target.getDouble(
                "legacy-combat.protection-scaling.extra-reduction-per-excess-level"));
        assertEquals(1, result.preserved().size());
        assertEquals(5, result.imported().size());
        assertFalse(target.contains("legacy-combat.unrecognized-value", true));
    }

    @Test
    void secondMappingIsIdempotent() {
        YamlConfiguration defaults = bundledDefaults();
        YamlConfiguration target = bundledDefaults();
        YamlConfiguration source = legacyConfiguration();

        assertTrue(LegacyCombatConfigMapping.apply(source, target, defaults).changed());
        LegacyCombatConfigMapping.MappingResult second =
                LegacyCombatConfigMapping.apply(source, target, defaults);

        assertFalse(second.changed());
        assertEquals(6, second.imported().size());
        assertTrue(second.preserved().isEmpty());
    }

    private YamlConfiguration bundledDefaults() {
        try (var stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (stream == null) {
                throw new AssertionError("config.yml missing");
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private YamlConfiguration legacyConfiguration() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("repair.enabled", false);
        source.set("repair.minimum-protection-level", 35);
        source.set("repair.online-scan-interval-ticks", 140);
        source.set("protection-scaling.enabled", false);
        source.set("protection-scaling.vanilla-total-level-cap", 24);
        source.set("protection-scaling.extra-reduction-per-excess-level", 0.004);
        source.set("protection-scaling.maximum-extra-reduction", 0.40);
        source.set("unrecognized-value", "must-not-be-imported");
        return source;
    }
}
