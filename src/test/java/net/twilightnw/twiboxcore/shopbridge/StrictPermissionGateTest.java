package net.twilightnw.twiboxcore.shopbridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StrictPermissionGateTest {
    private static final Set<String> REQUIRED = Set.of(
            "fancynpcs.command.npc.action.add", "shopkeeper.admin", "shopkeeper.remoteedit");

    @Test
    void requiresEveryPermission() {
        assertTrue(StrictPermissionGate.hasAll(REQUIRED, permission -> true));
        assertFalse(StrictPermissionGate.hasAll(REQUIRED,
                permission -> !permission.equals("shopkeeper.remoteedit")));
        assertFalse(StrictPermissionGate.hasAll(REQUIRED,
                permission -> permission.startsWith("shopkeeper.")));
    }

    @Test
    void failsClosedForMissingPolicy() {
        assertFalse(StrictPermissionGate.hasAll(Set.of(), permission -> true));
        assertFalse(StrictPermissionGate.hasAll(null, permission -> true));
        assertFalse(StrictPermissionGate.hasAll(REQUIRED, null));
    }
}
