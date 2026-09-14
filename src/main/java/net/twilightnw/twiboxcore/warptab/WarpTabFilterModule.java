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
 * Removes configured internal warp targets without owning or cancelling the
 * completion request. EssentialsX remains the authoritative suggestion source.
 */
public final class WarpTabFilterModule implements Listener {
    private static final List<String> DEFAULT_COMMANDS =
            List.of("warp", "ewarp", "essentials:warp");
    private static final List<String> DEFAULT_HIDDEN = List.of(
            "atlantistenspawna",
            "enddenspawna",
            "netherdenspawna",
            "siberdenspawna",
            "so_ukdiyardanspawna");

    private final TwiBoxCore plugin;
    private volatile WarpTabFilter filter;

    public WarpTabFilterModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        List<String> commands = plugin.getConfig().getStringList("warp-tab-filter.commands");
        List<String> hidden = plugin.getConfig().getStringList("warp-tab-filter.hidden-first-arguments");
        filter = new WarpTabFilter(
                commands.isEmpty() ? DEFAULT_COMMANDS : commands,
                hidden.isEmpty() ? DEFAULT_HIDDEN : hidden);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("WARP_TAB_FILTER_READY commands=" + filter.commandCount()
                + " hiddenFirstArguments=" + filter.hiddenArgumentCount());
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        filter = null;
    }

    public String status() {
        return filter == null ? "disabled" : "active(commands=" + filter.commandCount()
                + ",hidden=" + filter.hiddenArgumentCount() + ")";
    }

    public boolean selfTest(CommandSender sender) {
        WarpTabFilter activeFilter = filter;
        if (activeFilter == null) {
            sender.sendMessage("Warp tab filter self-test: FAIL (disabled)");
            return false;
        }
        WarpTabFilter.FilterResult<String> first = activeFilter.filterStrings("/warp e",
                List.of("end", "enddenspawna", "enddenspawnax"));
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
        WarpTabFilter.FilterResult<String> result =
                activeFilter.filterStrings(event.getBuffer(), event.getCompletions());
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
        WarpTabFilter.FilterResult<AsyncTabCompleteEvent.Completion> result = activeFilter.filter(
                event.getBuffer(), event.completions(), AsyncTabCompleteEvent.Completion::suggestion);
        if (result.changed()) {
            // Preserve rich completion tooltips and never mark the request handled.
            event.completions(result.suggestions());
        }
    }
}
