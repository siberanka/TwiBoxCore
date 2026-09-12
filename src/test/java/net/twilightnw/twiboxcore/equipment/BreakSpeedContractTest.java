package net.twilightnw.twiboxcore.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BreakSpeedContractTest {
    @Test
    void documentsProductionCompatibleMultipliers() {
        assertEquals(-0.60, BreakSpeed.toAttributeModifier(0.40), 1.0E-9);
        assertEquals(-0.80, BreakSpeed.toAttributeModifier(0.20), 1.0E-9);
        assertEquals(-0.90, BreakSpeed.toAttributeModifier(0.10), 1.0E-9);
    }

    @Test
    void clampsInvalidOrUnsafeMultipliers() {
        assertEquals(-0.60, BreakSpeed.toAttributeModifier(Double.NaN), 1.0E-9);
        assertEquals(-0.99, BreakSpeed.toAttributeModifier(-4.0), 1.0E-9);
        assertEquals(0.0, BreakSpeed.toAttributeModifier(4.0), 1.0E-9);
    }
}
