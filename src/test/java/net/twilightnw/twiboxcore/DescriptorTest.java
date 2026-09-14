package net.twilightnw.twiboxcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DescriptorTest {
    @Test
    void pluginDescriptorHasExpectedEntrypointAndLegacyCommands() {
        Map<String, Object> descriptor = yaml("plugin.yml");
        assertEquals("TwiBoxCore", descriptor.get("name"));
        assertEquals("net.twilightnw.twiboxcore.TwiBoxCore", descriptor.get("main"));
        assertEquals("1.21", descriptor.get("api-version"));

        Map<?, ?> commands = (Map<?, ?>) descriptor.get("commands");
        assertTrue(commands.containsKey("twiboxcore"));
        assertTrue(commands.containsKey("twilighttool"));
        assertTrue(commands.containsKey("twilightcombat"));
    }

    @Test
    void defaultConfigurationEnablesBothModules() {
        Map<String, Object> configuration = yaml("config.yml");
        assertEquals(4, configuration.get("config-version"));

        Map<?, ?> migration = (Map<?, ?>) configuration.get("migration");
        assertEquals(Boolean.TRUE, migration.get("import-legacy-configs"));

        Map<?, ?> modules = (Map<?, ?>) configuration.get("modules");
        assertEquals(Boolean.TRUE, modules.get("equipment-effects"));
        assertEquals(Boolean.TRUE, modules.get("legacy-combat"));

        Map<?, ?> equipment = (Map<?, ?>) configuration.get("equipment-effects");
        Map<?, ?> fatigue = (Map<?, ?>) equipment.get("fatigue");
        assertEquals(1, fatigue.get("amplifier"));
        assertEquals(20, fatigue.get("duration-ticks"));
        assertEquals(6, fatigue.get("arm-delay-ticks"));

        Map<?, ?> warning = (Map<?, ?>) equipment.get("wrong-tool-warning");
        assertEquals(20, warning.get("cooldown-ticks"));
        assertEquals("&cBu blok yalnızca ", warning.get("prefix"));
        assertEquals(" &cile kırılır!", warning.get("suffix"));
        Map<?, ?> toolNames = (Map<?, ?>) warning.get("tool-names");
        assertTrue(toolNames.keySet().containsAll(java.util.Set.of(
                "witch-shears", "cyber-shears", "zeus-hoe", "glacier-pickaxe")));

        Map<?, ?> speeds = (Map<?, ?>) equipment.get("break-speed-multipliers");
        assertEquals(0.60, speeds.get("witch-shears"));
        assertEquals(0.60, speeds.get("cyber-shears"));
        assertEquals(0.30, speeds.get("zeus-hoe"));
        assertEquals(0.15, speeds.get("glacier-pickaxe"));

        Map<?, ?> restricted = (Map<?, ?>) equipment.get("restricted-blocks");
        Map<?, ?> witchWool = (Map<?, ?>) restricted.get("witch-wool");
        Map<?, ?> cyberWool = (Map<?, ?>) restricted.get("cyber-wool");
        assertEquals("endd", witchWool.get("world"));
        assertEquals("arena", cyberWool.get("world"));
        assertEquals(java.util.List.of("BLACK_WOOL", "GRAY_WOOL"), witchWool.get("materials"));
        assertEquals(java.util.List.of("CYAN_WOOL", "LIGHT_BLUE_WOOL"), cyberWool.get("materials"));

        Map<?, ?> combat = (Map<?, ?>) configuration.get("legacy-combat");
        Map<?, ?> repair = (Map<?, ?>) combat.get("repair");
        assertFalse(repair.containsKey("online-scan-interval-ticks"));
        Map<?, ?> protection = (Map<?, ?>) combat.get("protection-scaling");
        assertEquals(20, protection.get("vanilla-total-level-cap"));
        assertEquals(0.0022, protection.get("extra-reduction-per-excess-level"));
        assertEquals(0.35, protection.get("maximum-extra-reduction"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yaml(String resource) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new Yaml().load(stream);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
