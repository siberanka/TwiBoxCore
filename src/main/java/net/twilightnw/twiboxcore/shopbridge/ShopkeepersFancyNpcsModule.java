package net.twilightnw.twiboxcore.shopbridge;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.SKShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.ShopkeepersStartupEvent;
import com.nisovin.shopkeepers.api.shopkeeper.ShopCreationData;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObject;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObjectType;
import com.nisovin.shopkeepers.shopobjects.ShopObjectData;
import com.nisovin.shopkeepers.util.data.serialization.InvalidDataException;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.actions.NpcAction.NpcActionData;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.twilightnw.twiboxcore.TwiBoxCore;
import net.twilightnw.twiboxcore.shopbridge.ShopkeeperRemoteActionResolver.ActionDescriptor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Entity-free Shopkeepers object type and guarded FancyNpcs editor bridge. */
public final class ShopkeepersFancyNpcsModule implements Listener {
    public static final String OBJECT_TYPE_ID = "fancynpc";
    private static final Set<String> SECURITY_PERMISSION_FLOOR = Set.of(
            "fancynpcs.command.npc.action.add",
            "shopkeeper.admin",
            "shopkeeper.remoteedit");

    private final TwiBoxCore plugin;
    private final Map<UUID, Long> lastEditTick = new HashMap<>();
    private final AtomicBoolean asyncWarningLogged = new AtomicBoolean();
    private FancyNpcShopObjectType objectType;
    private Set<String> requiredPermissions = SECURITY_PERMISSION_FLOOR;
    private boolean enabled;
    private boolean editorEnabled;
    private double maximumDistanceSquared = 64.0;
    private long cooldownTicks = 10L;

    public ShopkeepersFancyNpcsModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    /** Called from JavaPlugin#onLoad so saved fancynpc objects can deserialize. */
    public void load() {
        ensureObjectTypeRegistered();
    }

    public void enable() {
        if (enabled) {
            return;
        }
        loadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
        plugin.getLogger().info("SHOP_BRIDGE_READY objectType=" + OBJECT_TYPE_ID
                + " editor=" + editorEnabled + " permissionChecks=" + requiredPermissions.size());
    }

    public void disable() {
        if (!enabled) {
            return;
        }
        HandlerList.unregisterAll(this);
        lastEditTick.clear();
        enabled = false;
    }

    public String status() {
        boolean registered = isOurObjectTypeRegistered();
        return "enabled=" + enabled + ",registered=" + registered + ",editor=" + editorEnabled;
    }

    public boolean selfTest(CommandSender sender) {
        Optional<UUID> sample = ShopkeeperRemoteActionResolver.resolve(List.of(new ActionDescriptor(
                "console_command", "shopkeeper remote 00000000-0000-0000-0000-000000000001 {player}")));
        boolean passed = isOurObjectTypeRegistered()
                && objectType != null
                && OBJECT_TYPE_ID.equals(objectType.getIdentifier())
                && requiredPermissions.containsAll(SECURITY_PERMISSION_FLOOR)
                && maximumDistanceSquared >= 1.0
                && cooldownTicks >= 1L
                && sample.isPresent();
        sender.sendMessage("Shopkeepers/FancyNpcs self-test: " + (passed ? "PASS" : "FAIL"));
        return passed;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onShopkeepersStartup(ShopkeepersStartupEvent event) {
        ensureObjectTypeRegistered();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFancyNpcSpawn(NpcSpawnEvent event) {
        Npc npc = event.getNpc();
        Player player = event.getPlayer();
        if (npc.getData().getEquipment() == null || npc.getData().getEquipment().isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && npc.isShownFor(player)) {
                npc.update(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFancyNpcInteract(NpcInteractEvent event) {
        if (!editorEnabled || event.getInteractionType() != ActionTrigger.RIGHT_CLICK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (event.isAsynchronous()) {
            // Bukkit permission, registry and inventory APIs are only touched on the server thread.
            event.setCancelled(true);
            if (asyncWarningLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("FancyNpcs emitted an asynchronous interaction; editor checks were deferred safely.");
            }
            Npc npc = event.getNpc();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> handleEditorInteraction(player, npc, false));
            return;
        }

        if (handleEditorInteraction(player, event.getNpc(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastEditTick.remove(event.getPlayer().getUniqueId());
    }

    private boolean handleEditorInteraction(Player player, Npc npc, boolean scheduleOpen) {
        if (!player.isOnline() || !player.isSneaking() || !hasRequiredPermissions(player)) {
            return false;
        }
        Optional<UUID> linkedId = resolveLinkedShopkeeper(npc);
        if (linkedId.isEmpty()) {
            return false;
        }
        Shopkeeper shopkeeper = resolveValidFancyNpcShopkeeper(linkedId.get(), npc.getData().getId());
        if (shopkeeper == null || !isWithinRange(player, npc.getData().getLocation())) {
            return false;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        long previousTick = lastEditTick.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2);
        if (currentTick - previousTick < cooldownTicks) {
            return true;
        }
        lastEditTick.put(player.getUniqueId(), currentTick);

        Runnable opener = () -> openEditorAfterRecheck(player, npc, linkedId.get());
        if (scheduleOpen) {
            plugin.getServer().getScheduler().runTask(plugin, opener);
        } else {
            opener.run();
        }
        return true;
    }

    private void openEditorAfterRecheck(Player player, Npc npc, UUID expectedId) {
        if (!player.isOnline() || !player.isSneaking() || !hasRequiredPermissions(player)
                || !isWithinRange(player, npc.getData().getLocation())) {
            return;
        }
        Optional<UUID> currentId = resolveLinkedShopkeeper(npc);
        if (currentId.isEmpty() || !currentId.get().equals(expectedId)) {
            return;
        }
        Shopkeeper shopkeeper = resolveValidFancyNpcShopkeeper(expectedId, npc.getData().getId());
        if (shopkeeper == null || !shopkeeper.openEditorWindow(player)) {
            plugin.getLogger().fine("Shopkeeper editor did not open for " + player.getUniqueId());
        }
    }

    private Optional<UUID> resolveLinkedShopkeeper(Npc npc) {
        List<ActionDescriptor> actions = new ArrayList<>();
        appendActions(actions, npc.getData().getActions(ActionTrigger.RIGHT_CLICK));
        appendActions(actions, npc.getData().getActions(ActionTrigger.ANY_CLICK));
        return ShopkeeperRemoteActionResolver.resolve(actions);
    }

    private static void appendActions(List<ActionDescriptor> output, List<NpcActionData> actions) {
        if (actions == null) {
            return;
        }
        for (NpcActionData action : actions) {
            if (output.size() >= ShopkeeperRemoteActionResolver.MAX_ACTIONS) {
                // Force the bounded resolver to reject an oversized action set.
                output.add(new ActionDescriptor(null, null));
                return;
            }
            output.add(new ActionDescriptor(
                    action == null || action.action() == null ? null : action.action().getName(),
                    action == null ? null : action.value()));
        }
    }

    private Shopkeeper resolveValidFancyNpcShopkeeper(UUID id, String npcId) {
        if (!ShopkeepersAPI.isEnabled()) {
            return null;
        }
        Shopkeeper shopkeeper = ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByUniqueId(id);
        if (shopkeeper == null || !shopkeeper.isValid() || shopkeeper.getShopObject() == null
                || shopkeeper.getShopObject().getType() == null
                || !OBJECT_TYPE_ID.equals(shopkeeper.getShopObject().getType().getIdentifier())
                || !(shopkeeper.getShopObject() instanceof FancyNpcShopObject fancyObject)
                || !fancyObject.isLinkedTo(npcId)) {
            return null;
        }
        return shopkeeper;
    }

    private boolean hasRequiredPermissions(Player player) {
        return StrictPermissionGate.hasAll(requiredPermissions, player::hasPermission);
    }

    private boolean isWithinRange(Player player, Location npcLocation) {
        if (npcLocation == null || npcLocation.getWorld() == null
                || player.getWorld() != npcLocation.getWorld()) {
            return false;
        }
        return player.getLocation().distanceSquared(npcLocation) <= maximumDistanceSquared;
    }

    private void loadSettings() {
        String root = "shopkeepers-fancynpcs.admin-edit.";
        editorEnabled = plugin.getConfig().getBoolean(root + "enabled", true);
        double distance = plugin.getConfig().getDouble(root + "max-distance", 8.0);
        if (!Double.isFinite(distance)) {
            distance = 8.0;
        }
        distance = Math.max(1.0, Math.min(16.0, distance));
        maximumDistanceSquared = distance * distance;
        cooldownTicks = Math.max(1L, Math.min(100L,
                plugin.getConfig().getLong(root + "cooldown-ticks", 10L)));

        LinkedHashSet<String> permissions = new LinkedHashSet<>(SECURITY_PERMISSION_FLOOR);
        for (String permission : plugin.getConfig().getStringList(root + "required-permissions")) {
            String normalized = permission == null ? "" : permission.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.matches("[a-z0-9_.-]{1,128}")) {
                permissions.add(normalized);
            }
        }
        requiredPermissions = Collections.unmodifiableSet(permissions);
    }

    private void ensureObjectTypeRegistered() {
        try {
            var registry = ((SKShopkeepersPlugin) ShopkeepersAPI.getPlugin()).getShopObjectTypeRegistry();
            var existing = registry.get(OBJECT_TYPE_ID);
            if (existing != null) {
                if (existing instanceof FancyNpcShopObjectType ours) {
                    objectType = ours;
                    return;
                }
                throw new IllegalStateException("Shopkeepers object type collision: " + OBJECT_TYPE_ID
                        + " is already owned by " + existing.getClass().getName());
            }
            FancyNpcShopObjectType created = new FancyNpcShopObjectType();
            registry.register(created);
            objectType = created;
            plugin.getLogger().info("Registered entity-free '" + OBJECT_TYPE_ID + "' Shopkeepers object type.");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not register the fancynpc Shopkeepers object type; refusing unsafe partial startup.", exception);
            throw exception;
        }
    }

    private boolean isOurObjectTypeRegistered() {
        try {
            return ((SKShopkeepersPlugin) ShopkeepersAPI.getPlugin())
                    .getShopObjectTypeRegistry().get(OBJECT_TYPE_ID) instanceof FancyNpcShopObjectType;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static final class FancyNpcShopObjectType extends AbstractShopObjectType<FancyNpcShopObject> {
        public FancyNpcShopObjectType() {
            super(OBJECT_TYPE_ID, Collections.emptyList(), "shopkeeper.fancynpc", FancyNpcShopObject.class);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "FancyNpcs";
        }

        @Override
        public boolean mustBeSpawned() {
            return true;
        }

        @Override
        public boolean validateSpawnLocation(Player player, Location location, BlockFace blockFace) {
            return location != null && location.getWorld() != null;
        }

        @Override
        public FancyNpcShopObject createObject(AbstractShopkeeper shopkeeper, ShopCreationData creationData) {
            return new FancyNpcShopObject(this, shopkeeper, creationData);
        }
    }

    public static final class FancyNpcShopObject extends AbstractShopObject {
        private final FancyNpcShopObjectType type;
        private String npcId;
        private boolean spawned;

        private FancyNpcShopObject(
                FancyNpcShopObjectType type,
                AbstractShopkeeper shopkeeper,
                ShopCreationData creationData) {
            super(shopkeeper, creationData);
            this.type = type;
        }

        @Override
        public void load(ShopObjectData data) throws InvalidDataException {
            super.load(data);
            npcId = data.getStringOrDefault("npcId", null);
        }

        @Override
        public void save(ShopObjectData data, boolean saveAll) {
            super.save(data, saveAll);
            if (npcId != null && !npcId.isBlank()) {
                data.set("npcId", npcId);
            }
        }

        @Override
        public FancyNpcShopObjectType getType() {
            return type;
        }

        @Override
        public String getId() {
            return spawned ? "fancynpc-" + shopkeeper.getId() : null;
        }

        @Override
        public boolean isSpawned() {
            return spawned;
        }

        @Override
        public boolean isActive() {
            return spawned && shopkeeper.isActive();
        }

        @Override
        public boolean spawn() {
            spawned = true;
            onIdChanged();
            onSpawnSucceeded();
            return true;
        }

        @Override
        public void despawn() {
            spawned = false;
            onIdChanged();
        }

        @Override
        public Location getLocation() {
            return shopkeeper.getLocation();
        }

        @Override
        public boolean move() {
            return true;
        }

        @Override
        public Location getTickVisualizationParticleLocation() {
            return shopkeeper.getLocation();
        }

        @Override
        public void setName(String name) {
            // Packet NPC names remain owned by FancyNpcs.
        }

        @Override
        public String getName() {
            return null;
        }

        boolean isLinkedTo(String expectedNpcId) {
            return npcId != null && expectedNpcId != null && npcId.equalsIgnoreCase(expectedNpcId);
        }
    }
}
