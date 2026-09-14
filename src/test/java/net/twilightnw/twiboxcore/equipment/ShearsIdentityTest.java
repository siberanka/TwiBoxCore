package net.twilightnw.twiboxcore.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShearsIdentityTest {
    @Test
    void distinguishesCyberAndWitchShearsFromLegacyLore() {
        assertEquals("cyber_shears", EquipmentEffectsModule.classifyLegacyShearsLore(
                List.of("Bu makas Siber Yününü kırmana yarar!")));
        assertEquals("witch_shears", EquipmentEffectsModule.classifyLegacyShearsLore(
                List.of("Bu makas Cadı Yününü kırmana yarar!")));
    }

    @Test
    void doesNotClaimOrdinaryShears() {
        assertEquals("none", EquipmentEffectsModule.classifyLegacyShearsLore(
                List.of("Sıradan bir makas")));
        assertEquals("none", EquipmentEffectsModule.classifyLegacyShearsLore(List.of()));
    }
}
