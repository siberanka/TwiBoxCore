package net.twilightnw.twiboxcore.warpmenu;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.twilightnw.twiboxcore.TwiBoxCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Opens a fixed DeluxeMenus menu only for an exact bare warp command. */
public final class WarpMenuModule implements Listener {
    private static final List<String> DEFAULT_COMMANDS = List.of("warp");
    private static final String DEFAULT_MENU = "warplar";

    private final TwiBoxCore plugin;
    private BareWarpCommandMatcher matcher;
    private String menu;

    public WarpMenuModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        List<String> commands = plugin.getConfig().getStringList("warp-menu.commands");
        matcher = new BareWarpCommandMatcher(commands.isEmpty() ? DEFAULT_COMMANDS : commands);
        menu = normalizeMenu(plugin.getConfig().getString("warp-menu.menu", DEFAULT_MENU));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("WARP_MENU_READY commands=" + matcher.labelCount() + " menu=" + menu
                + " deluxeMenus=" + (plugin.getServer().getPluginManager().isPluginEnabled("DeluxeMenus")
                ? "enabled" : "awaiting-enable"));
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        matcher = null;
        menu = null;
    }

    public String status() {
        return matcher == null ? "disabled" : "active(commands=" + matcher.labelCount() + ",menu=" + menu + ")";
    }

    public boolean selfTest(CommandSender sender) {
        BareWarpCommandMatcher active = matcher;
        boolean passed = active != null
                && active.matches("/warp")
                && active.matches("  /WARP  ")
                && !active.matches("/warp end")
                && !active.matches("/warps")
                && menu != null;
        sender.sendMessage("Warp menu self-test: " + (passed ? "PASS" : "FAIL"));
        return passed;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        BareWarpCommandMatcher active = matcher;
        String activeMenu = menu;
        if (active == null || activeMenu == null || !active.matches(event.getMessage())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        String playerName = player.getName();
        if (!playerName.matches("[A-Za-z0-9_]{1,16}")) {
            plugin.getLogger().warning("Refused to dispatch warp menu for an invalid player name.");
            return;
        }

        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()) return;
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "deluxemenus open " + activeMenu + " " + playerName);
            if (!dispatched) {
                plugin.getLogger().log(Level.WARNING,
                        "DeluxeMenus rejected the configured warp menu: {0}", activeMenu);
                player.sendMessage("§cWarp menüsü şu anda açılamıyor.");
            }
        });
    }

    static String normalizeMenu(String configured) {
        if (configured == null) return DEFAULT_MENU;
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,64}") ? normalized : DEFAULT_MENU;
    }
}
