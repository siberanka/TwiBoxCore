package net.twilightnw.twiboxcore.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyArmorValuesTest {
    @Test
    void reproducesProtectionThirtyEightSet() {
        assertValues(LegacyArmorValues.Piece.HEAD, 8.0);
        assertValues(LegacyArmorValues.Piece.CHEST, 14.0);
        assertValues(LegacyArmorValues.Piece.LEGS, 12.0);
        assertValues(LegacyArmorValues.Piece.FEET, 8.0);
    }

    private void assertValues(LegacyArmorValues.Piece piece, double expectedArmor) {
        LegacyArmorValues values = LegacyArmorValues.calculate(piece, 38);
        assertEquals(expectedArmor, values.armor());
        assertEquals(3.0, values.toughness());
        assertEquals(0.1, values.knockbackResistance());
    }
}
