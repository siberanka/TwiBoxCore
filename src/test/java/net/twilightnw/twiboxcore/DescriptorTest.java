package net.twilightnw.twiboxcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(2, configuration.get("config-version"));

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

        Map<?, ?> speeds = (Map<?, ?>) equipment.get("break-speed-multipliers");
        assertEquals(0.40, speeds.get("witch-shears"));
        assertEquals(0.20, speeds.get("zeus-hoe"));
        assertEquals(0.10, speeds.get("glacier-pickaxe"));

        Map<?, ?> combat = (Map<?, ?>) configuration.get("legacy-combat");
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
