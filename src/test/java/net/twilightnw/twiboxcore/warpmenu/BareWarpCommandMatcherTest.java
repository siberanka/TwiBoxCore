package net.twilightnw.twiboxcore.warpmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BareWarpCommandMatcherTest {
    @Test
    void matchesOnlyExactConfiguredBareCommands() {
        BareWarpCommandMatcher matcher = new BareWarpCommandMatcher(List.of("warp", "/EWARP"));

        assertTrue(matcher.matches("/warp"));
        assertTrue(matcher.matches("  /WARP  "));
        assertTrue(matcher.matches("/ewarp"));
        assertFalse(matcher.matches("/warp end"));
        assertFalse(matcher.matches("/warp\nstop"));
        assertFalse(matcher.matches("/warps"));
        assertFalse(matcher.matches("warp"));
    }

    @Test
    void rejectsMalformedConfigurationAndOversizedInput() {
        BareWarpCommandMatcher matcher = new BareWarpCommandMatcher(List.of(
                "warp", "warp injected", "x".repeat(65)));

        assertEquals(1, matcher.labelCount());
        assertFalse(matcher.matches("/" + "x".repeat(65)));
        assertEquals("warplar", WarpMenuModule.normalizeMenu("invalid menu; stop"));
        assertEquals("warplar_2", WarpMenuModule.normalizeMenu(" WARPLaR_2 "));
    }
}
