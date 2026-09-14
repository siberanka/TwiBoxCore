package net.twilightnw.twiboxcore.warptab;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import java.util.List;
import net.twilightnw.twiboxcore.TwiBoxCore;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

/**
 * Reconciles permission-granted public warp suggestions and removes configured
 * internal targets without owning or cancelling the completion request.
 */
public final class WarpTabFilterModule implements Listener {
    private static final List<String> DEFAULT_COMMANDS =
            List.of("warp", "ewarp", "essentials:warp");
    private static final List<String> DEFAULT_HIDDEN = List.of(
            "atlantistenspawna",
            "enddenspawna",
            "netherdenspawna",
            "siberdenspawna",
            "so_ukdiyardanspawna",
            "soğukdiyardanspawna");
    private static final List<String> DEFAULT_VISIBLE = List.of(
            "atlantis", "boss", "end", "kasa", "kasalar", "kasılma", "nether",
            "shulker", "siber", "soğukdiyar", "spawn", "takas");

    private final TwiBoxCore plugin;
    private volatile WarpTabFilter filter;

    public WarpTabFilterModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        List<String> commands = plugin.getConfig().getStringList("warp-tab-filter.commands");
        List<String> hidden = plugin.getConfig().getStringList("warp-tab-filter.hidden-first-arguments");
        List<String> visible = plugin.getConfig().getStringList("warp-tab-filter.visible-first-arguments");
        filter = new WarpTabFilter(
                commands.isEmpty() ? DEFAULT_COMMANDS : commands,
                hidden.isEmpty() ? DEFAULT_HIDDEN : hidden,
                visible.isEmpty() ? DEFAULT_VISIBLE : visible);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("WARP_TAB_FILTER_READY commands=" + filter.commandCount()
                + " hiddenFirstArguments=" + filter.hiddenArgumentCount()
                + " visibleFirstArguments=" + filter.visibleArgumentCount());
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        filter = null;
    }

    public String status() {
        return filter == null ? "disabled" : "active(commands=" + filter.commandCount()
                + ",hidden=" + filter.hiddenArgumentCount()
                + ",visible=" + filter.visibleArgumentCount() + ")";
    }

    public boolean selfTest(CommandSender sender) {
        WarpTabFilter activeFilter = filter;
        if (activeFilter == null) {
            sender.sendMessage("Warp tab filter self-test: FAIL (disabled)");
            return false;
        }
        WarpTabFilter.FilterResult<String> first = activeFilter.reconcileStrings("/warp e",
                List.of("end", "enddenspawna", "enddenspawnax"), candidate -> true);
        WarpTabFilter.FilterResult<String> second = activeFilter.filterStrings("/warp end e",
                List.of("enddenspawna"));
        boolean passed = first.changed()
                && first.suggestions().equals(List.of("end", "enddenspawnax"))
                && !second.changed()
                && second.suggestions().equals(List.of("enddenspawna"));
        sender.sendMessage("Warp tab filter self-test: " + (passed ? "PASS" : "FAIL"));
        return passed;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        WarpTabFilter activeFilter = filter;
        if (activeFilter == null) {
            return;
        }
        WarpTabFilter.FilterResult<String> result = activeFilter.reconcileStrings(
                event.getBuffer(), event.getCompletions(),
                candidate -> event.getSender().hasPermission("essentials.warps." + candidate));
        if (result.changed()) {
            event.setCompletions(result.suggestions());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!event.isCommand()) {
            return;
        }
        WarpTabFilter activeFilter = filter;
        if (activeFilter == null) {
            return;
        }
        WarpTabFilter.FilterResult<AsyncTabCompleteEvent.Completion> result = activeFilter.reconcile(
                event.getBuffer(), event.completions(),
                AsyncTabCompleteEvent.Completion::suggestion,
                AsyncTabCompleteEvent.Completion::completion,
                candidate -> event.getSender().hasPermission("essentials.warps." + candidate));
        if (result.changed()) {
            // Preserve existing rich tooltips and never mark the request handled.
            event.completions(result.suggestions());
        }
    }
}
