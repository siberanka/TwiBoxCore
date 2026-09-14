package net.twilightnw.twiboxcore.shopbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.twilightnw.twiboxcore.shopbridge.ShopkeeperRemoteActionResolver.ActionDescriptor;
import org.junit.jupiter.api.Test;

class ShopkeeperRemoteActionResolverTest {
    private static final UUID SHOP_ID = UUID.fromString("12345678-1234-4234-8234-1234567890ab");

    @Test
    void acceptsOnlyTheExactRemoteConsoleAction() {
        assertEquals(SHOP_ID, resolve("console_command",
                "shopkeeper remote 12345678-1234-4234-8234-1234567890ab {player}"));
        assertEquals(SHOP_ID, resolve("CONSOLE_COMMAND",
                "/shopkeepers remote 12345678-1234-4234-8234-1234567890ab {player}"));
    }

    @Test
    void rejectsPlayerCommandsMalformedTargetsAndInjectedSuffixes() {
        assertEmpty("player_command", "shopkeeper remote " + SHOP_ID + " {player}");
        assertEmpty("console_command", "shopkeeper remote not-a-uuid {player}");
        assertEmpty("console_command", "shopkeeper remote " + SHOP_ID + " {player}; op {player}");
        assertEmpty("console_command", "shopkeeper remote " + SHOP_ID + " {player}\nstop");
        assertEmpty("console_command", "shopkeeper remote " + SHOP_ID + " another-player");
    }

    @Test
    void rejectsAmbiguousOrOversizedActionSets() {
        UUID second = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        assertTrue(ShopkeeperRemoteActionResolver.resolve(List.of(
                descriptor("shopkeeper remote " + SHOP_ID + " {player}"),
                descriptor("shopkeeper remote " + second + " {player}"))).isEmpty());

        List<ActionDescriptor> oversized = new ArrayList<>();
        for (int index = 0; index <= ShopkeeperRemoteActionResolver.MAX_ACTIONS; index++) {
            oversized.add(descriptor("shopkeeper remote " + SHOP_ID + " {player}"));
        }
        assertTrue(ShopkeeperRemoteActionResolver.resolve(oversized).isEmpty());
    }

    private static UUID resolve(String actionName, String value) {
        return ShopkeeperRemoteActionResolver.resolve(List.of(new ActionDescriptor(actionName, value)))
                .orElseThrow();
    }

    private static void assertEmpty(String actionName, String value) {
        assertTrue(ShopkeeperRemoteActionResolver.resolve(List.of(new ActionDescriptor(actionName, value))).isEmpty());
    }

    private static ActionDescriptor descriptor(String value) {
        return new ActionDescriptor("console_command", value);
    }
}
