package net.twilightnw.twiboxcore.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionScalingTest {
    @Test
    void preservesLegacyDefaultsAndCap() {
        ProtectionScaling scaling = new ProtectionScaling(20, 0.0022, 0.35);
        assertEquals(0.0, scaling.extraReduction(20));
        assertEquals(0.088, scaling.extraReduction(60), 1.0E-9);
        assertEquals(0.35, scaling.extraReduction(1000), 1.0E-9);
    }

    @Test
    void isMonotonicForKnownLegacySets() {
        ProtectionScaling scaling = new ProtectionScaling(20, 0.0022, 0.35);
        int[] totals = {20, 60, 80, 92, 112, 132, 152, 180};
        double previous = -1.0;
        for (int total : totals) {
            double current = scaling.extraReduction(total);
            assertTrue(current >= previous);
            previous = current;
        }
    }

    @Test
    void rejectsUnsafeConfigurationRanges() {
        ProtectionScaling scaling = new ProtectionScaling(-5, Double.NaN, 99.0);
        assertEquals(0, scaling.vanillaTotalLevelCap());
        assertEquals(0.0, scaling.reductionPerExcessLevel());
        assertEquals(0.75, scaling.maximumExtraReduction());
    }
}
