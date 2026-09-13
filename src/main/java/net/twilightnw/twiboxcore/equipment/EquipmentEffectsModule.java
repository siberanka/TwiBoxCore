package net.twilightnw.twiboxcore.equipment;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.twilightnw.twiboxcore.TwiBoxCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Preserves the special-tool behavior from TwilightEquipmentEffectsGuard.
 *
 * <p>The module keeps ItemTag's existing PDC format authoritative, repairs
 * legacy tool stacks, restores Mining Fatigue after hand changes, and rejects
 * progress-carry-over attempts on restricted blocks.</p>
 */
public final class EquipmentEffectsModule implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String GLACIER_NAME = "&x&0&0&8&b&f&bB&x&1&1&9&3&f&8u&x&2&2&9&c&f&5z&x&3&3&a&4&f&2u&x&4&4&a&c&e&fl &x&5&5&b&5&e&bK&x&6&6&b&d&e&8a&x&7&7&c&5&e&5z&x&8&8&c&e&e&2m&x&9&9&d&6&d&fa";
    private static final String GLACIER_LORE = "&x&0&0&8&b&f&bB&x&0&6&8&e&f&au &x&0&c&9&1&f&9k&x&1&2&9&4&f&8a&x&1&8&9&7&f&7z&x&1&d&9&9&f&6m&x&2&3&9&c&f&5a &x&2&9&9&f&f&3b&x&2&f&a&2&f&2u&x&3&5&a&5&f&1z&x&3&b&a&8&f&0l&x&4&1&a&b&e&fa&x&4&7&a&e&e&er&x&4&d&b&1&e&dı &x&5&2&b&3&e&ck&x&5&8&b&6&e&bı&x&5&e&b&9&e&ar&x&6&4&b&c&e&9m&x&6&a&b&f&e&8a&x&7&0&c&2&e&7n&x&7&6&c&5&e&5a &x&7&c&c&8&e&4y&x&8&1&c&a&e&3a&x&8&7&c&d&e&2r&x&8&d&d&0&e&1a&x&9&3&d&3&e&0r&x&9&9&d&6&d&f.";

    private final TwiBoxCore plugin;
    private final NamespacedKey effectsListKey = new NamespacedKey("itemtag", "effects_list");
    private final NamespacedKey effectsEquipsKey = new NamespacedKey("itemtag", "effects_equips");
    private final NamespacedKey specialToolKey = new NamespacedKey("twilightnw", "special_tool");
    private final NamespacedKey breakSpeedKey = new NamespacedKey("twilightnw", "restricted_tool_break_slowdown");
    private final Map<UUID, Long> armedAt = new HashMap<>();
    private final Map<UUID, String> handState = new HashMap<>();
    private final Map<UUID, MiningAttempt> miningAttempts = new HashMap<>();
    private final Set<UUID> guardApplied = new HashSet<>();
    private final Map<BlockRuleType, BlockRule> rules = new HashMap<>();
    private int targetAmplifier;
    private int effectTicks;
    private int armTicks;
    private double witchModifier;
    private double zeusModifier;
    private double glacierModifier;
    private long tick;
    private BukkitTask heartbeatTask;

    public EquipmentEffectsModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int normalized = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            normalized += normalizeInventory(player);
            arm(player);
        }
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::heartbeat, 1L, 1L);
        plugin.getLogger().info("EQUIPMENT_READY fatigueLevel=" + (targetAmplifier + 1)
                + " armTicks=" + armTicks + " onlineItemsNormalized=" + normalized);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeBreakSpeedModifier(player);
        }
        armedAt.clear();
        handState.clear();
        miningAttempts.clear();
        guardApplied.clear();
    }

    public String status() {
        return "active(level=" + (targetAmplifier + 1) + ",tracked=" + handState.size() + ")";
    }

    private void loadSettings() {
        String root = "equipment-effects.";
        targetAmplifier = Math.max(0, plugin.getConfig().getInt(root + "fatigue.amplifier", 1));
        effectTicks = Math.max(5, plugin.getConfig().getInt(root + "fatigue.duration-ticks", 20));
        armTicks = Math.max(0, plugin.getConfig().getInt(root + "fatigue.arm-delay-ticks", 6));
        witchModifier = BreakSpeed.toAttributeModifier(plugin.getConfig().getDouble(root + "break-speed-multipliers.witch-shears", 0.60));
        zeusModifier = BreakSpeed.toAttributeModifier(plugin.getConfig().getDouble(root + "break-speed-multipliers.zeus-hoe", 0.30));
        glacierModifier = BreakSpeed.toAttributeModifier(plugin.getConfig().getDouble(root + "break-speed-multipliers.glacier-pickaxe", 0.15));
        rules.clear();
        rules.put(BlockRuleType.WITCH_WOOL, loadRule(root + "restricted-blocks.witch-wool", ToolKind.WITCH_SHEARS, true));
        rules.put(BlockRuleType.ATLANTIS_SPONGE, loadRule(root + "restricted-blocks.atlantis-sponge", ToolKind.ZEUS_HOE, true));
        rules.put(BlockRuleType.GLACIER_ICE, loadRule(root + "restricted-blocks.glacier-ice", ToolKind.GLACIER_PICKAXE, false));
    }

    private BlockRule loadRule(String path, ToolKind required, boolean requiresFatigue) {
        String world = plugin.getConfig().getString(path + ".world", "");
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : plugin.getConfig().getStringList(path + ".materials")) {
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Ignoring invalid block material at " + path + ": " + value);
            } else {
                materials.add(material);
            }
        }
        return new BlockRule(world, materials, required, requiresFatigue);
    }

    public boolean handleToolCommand(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[0].equalsIgnoreCase("give") || !args[1].equalsIgnoreCase("buzul")) {
            return false;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("Player is not online: " + args[2]);
            return true;
        }
        Map<Integer, ItemStack> remaining = target.getInventory().addItem(createGlacierPickaxe());
        remaining.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage("Issued canonical Buzul Kazma to " + target.getName() + ".");
        return true;
    }

    public boolean selfTest(CommandSender sender) {
        boolean passed = closeTo(1.0 + witchModifier, 0.60)
                && closeTo(1.0 + zeusModifier, 0.30)
                && closeTo(1.0 + glacierModifier, 0.15)
                && rules.values().stream().allMatch(rule -> !rule.world().isBlank() && !rule.materials().isEmpty());
        sender.sendMessage("Equipment self-test: " + (passed ? "PASS" : "FAIL"));
        return passed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int normalized = normalizeInventory(event.getPlayer());
            arm(event.getPlayer());
            if (normalized > 0) {
                plugin.getLogger().info("Normalized " + normalized + " legacy tool(s) for " + event.getPlayer().getName());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        armedAt.remove(id);
        handState.remove(id);
        miningAttempts.remove(id);
        guardApplied.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack incoming = player.getInventory().getItem(event.getNewSlot());
        if (normalize(incoming)) {
            player.getInventory().setItem(event.getNewSlot(), incoming);
        }
        arm(player);
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        normalize(event.getMainHandItem());
        normalize(event.getOffHandItem());
        arm(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            arm(player);
            normalize(event.getCurrentItem());
            normalize(event.getCursor());
            Bukkit.getScheduler().runTask(plugin, () -> reconcile(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            normalize(event.getItem().getItemStack());
            arm(player);
            Bukkit.getScheduler().runTask(plugin, () -> reconcile(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        arm(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        BlockRule rule = ruleFor(event.getBlock());
        if (rule == null || player.getGameMode() == GameMode.CREATIVE) {
            miningAttempts.remove(player.getUniqueId());
            return;
        }
        if (rule.required() != toolKind(player.getInventory().getItemInMainHand())
                || (rule.requiresFatigue() ? !isReady(player) : !isArmed(player))) {
            miningAttempts.remove(player.getUniqueId());
            rejectMining(player, event.getBlock(), () -> event.setCancelled(true));
            return;
        }
        miningAttempts.put(player.getUniqueId(), MiningAttempt.of(event.getBlock(), rule, tick));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BlockRule rule = ruleFor(event.getBlock());
        if (rule == null || player.getGameMode() == GameMode.CREATIVE) {
            miningAttempts.remove(player.getUniqueId());
            return;
        }
        MiningAttempt attempt = miningAttempts.remove(player.getUniqueId());
        long readyAt = armedAt.getOrDefault(player.getUniqueId(), tick) + armTicks;
        boolean validStart = attempt != null && attempt.matches(event.getBlock(), rule) && attempt.startedAt() >= readyAt;
        boolean correctTool = rule.required() == toolKind(player.getInventory().getItemInMainHand());
        boolean ready = rule.requiresFatigue() ? isReady(player) : isArmed(player);
        if (!correctTool || !ready || !validStart) {
            rejectMining(player, event.getBlock(), () -> event.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        miningAttempts.remove(event.getPlayer().getUniqueId());
    }

    private boolean isReady(Player player) {
        return reconcile(player) && isArmed(player);
    }

    private boolean isArmed(Player player) {
        return tick - armedAt.getOrDefault(player.getUniqueId(), tick) >= armTicks;
    }

    private void rejectMining(Player player, Block block, Runnable cancel) {
        cancel.run();
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private void heartbeat() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                String currentState = state(player);
                String previousState = handState.put(player.getUniqueId(), currentState);
                if (previousState != null && !previousState.equals(currentState)) {
                    arm(player);
                }
                reconcile(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Equipment guard failed for " + player.getName(), exception);
            }
        }
    }

    private void arm(Player player) {
        armedAt.put(player.getUniqueId(), tick);
        miningAttempts.remove(player.getUniqueId());
    }

    private boolean reconcile(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();
        reconcileBreakSpeed(player, toolKind(main));
        boolean migrated = normalize(main) | normalize(off);
        boolean targetHeld = isFatigueTool(main) || isFatigueTool(off);
        PotionEffect current = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);

        if (!targetHeld) {
            if (guardApplied.remove(player.getUniqueId()) && current != null
                    && current.getAmplifier() == targetAmplifier && current.getDuration() <= effectTicks) {
                player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            }
            return false;
        }
        if (migrated && current != null && current.getAmplifier() != targetAmplifier) {
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
            current = null;
        }
        boolean recovered = current == null || current.getAmplifier() < targetAmplifier;
        if (recovered || (current.getAmplifier() == targetAmplifier && current.getDuration() <= 5)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    effectTicks, targetAmplifier, false, false, false));
            guardApplied.add(player.getUniqueId());
            if (recovered) {
                arm(player);
            }
            current = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        }
        return current != null && current.getAmplifier() >= targetAmplifier;
    }

    private void reconcileBreakSpeed(Player player, ToolKind toolKind) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(breakSpeedKey);
        if (toolKind == ToolKind.NONE) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            return;
        }
        double modifier = switch (toolKind) {
            case WITCH_SHEARS -> witchModifier;
            case ZEUS_HOE -> zeusModifier;
            case GLACIER_PICKAXE -> glacierModifier;
            case NONE -> throw new IllegalStateException("NONE handled before multiplier selection");
        };
        if (existing != null && closeTo(existing.getAmount(), modifier)
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addTransientModifier(new AttributeModifier(
                breakSpeedKey, modifier, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeBreakSpeedModifier(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance != null) {
            AttributeModifier existing = instance.getModifier(breakSpeedKey);
            if (existing != null) {
                instance.removeModifier(existing);
            }
        }
    }

    private int normalizeInventory(Player player) {
        int changed = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (normalize(item)) {
                inventory.setItem(slot, item);
                changed++;
            }
        }
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            ItemStack item = player.getEnderChest().getItem(slot);
            if (normalize(item)) {
                player.getEnderChest().setItem(slot, item);
                changed++;
            }
        }
        return changed;
    }

    private boolean normalize(ItemStack item) {
        ToolKind kind = toolKind(item);
        if (kind == ToolKind.NONE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (!meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            changed = true;
        }
        if (!meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE)) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            changed = true;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!kind.canonicalId.equals(pdc.get(specialToolKey, PersistentDataType.STRING))) {
            pdc.set(specialToolKey, PersistentDataType.STRING, kind.canonicalId);
            changed = true;
        }
        if (kind == ToolKind.GLACIER_PICKAXE) {
            changed |= meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
            changed |= meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            // Attribute visibility is normalized, but enchantment visibility is
            // deliberately preserved exactly as the source item configured it.
            for (ItemFlag flag : List.of(ItemFlag.HIDE_ATTRIBUTES)) {
                if (!meta.hasItemFlag(flag)) {
                    meta.addItemFlags(flag);
                    changed = true;
                }
            }
            if (changed) {
                item.setItemMeta(meta);
            }
            return changed;
        }
        if (kind == ToolKind.WITCH_SHEARS && !Boolean.TRUE.equals(meta.getEnchantmentGlintOverride())) {
            meta.setEnchantmentGlintOverride(true);
            changed = true;
        }
        if (kind == ToolKind.ZEUS_HOE) {
            changed |= meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            for (ItemFlag flag : List.of(ItemFlag.HIDE_ATTRIBUTES)) {
                if (!meta.hasItemFlag(flag)) {
                    meta.addItemFlags(flag);
                    changed = true;
                }
            }
        }
        String expectedEquips = kind == ToolKind.WITCH_SHEARS
                ? "HAND;OFF_HAND;FEET;LEGS;CHEST;HEAD" : "HAND;OFF_HAND";
        if (!expectedEquips.equals(pdc.get(effectsEquipsKey, PersistentDataType.STRING))) {
            pdc.set(effectsEquipsKey, PersistentDataType.STRING, expectedEquips);
            changed = true;
        }
        String effects = pdc.get(effectsListKey, PersistentDataType.STRING);
        if (effects == null) {
            effects = kind == ToolKind.WITCH_SHEARS
                    ? "SLOW_DIGGING,1,true,true,true" : "SLOW_DIGGING,1,true,false,false";
            pdc.set(effectsListKey, PersistentDataType.STRING, effects);
            changed = true;
        }
        String[] parts = effects.split(",", -1);
        if (parts.length >= 2 && !Integer.toString(targetAmplifier).equals(parts[1])) {
            try {
                Integer.parseInt(parts[1]);
                parts[1] = Integer.toString(targetAmplifier);
                pdc.set(effectsListKey, PersistentDataType.STRING, String.join(",", parts));
                changed = true;
            } catch (NumberFormatException ignored) {
                // Leave malformed third-party data untouched.
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    private boolean isFatigueTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Material type = item.getType();
        if (type != Material.SHEARS && type != Material.DIAMOND_HOE) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String effects = pdc.get(effectsListKey, PersistentDataType.STRING);
        String equips = pdc.get(effectsEquipsKey, PersistentDataType.STRING);
        if (effects == null || equips == null || !effects.startsWith("SLOW_DIGGING,")) {
            return false;
        }
        return (type == Material.SHEARS && equips.equals("HAND;OFF_HAND;FEET;LEGS;CHEST;HEAD"))
                || (type == Material.DIAMOND_HOE && equips.equals("HAND;OFF_HAND"));
    }

    private String state(Player player) {
        return signature(player.getInventory().getItemInMainHand()) + '|'
                + signature(player.getInventory().getItemInOffHand());
    }

    private String signature(ItemStack item) {
        ToolKind kind = toolKind(item);
        if (kind == ToolKind.NONE) {
            return "-";
        }
        return kind + ":" + item.getItemMeta().getPersistentDataContainer()
                .get(effectsListKey, PersistentDataType.STRING);
    }

    private ToolKind toolKind(ItemStack item) {
        if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            String id = item.getItemMeta().getPersistentDataContainer()
                    .get(specialToolKey, PersistentDataType.STRING);
            ToolKind marked = ToolKind.fromCanonicalId(id);
            if (marked.matches(item.getType())) {
                return marked;
            }
        }
        if (isFatigueTool(item)) {
            return item.getType() == Material.SHEARS ? ToolKind.WITCH_SHEARS : ToolKind.ZEUS_HOE;
        }
        return isGlacierPickaxe(item) ? ToolKind.GLACIER_PICKAXE : ToolKind.NONE;
    }

    private boolean isGlacierPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_PICKAXE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Component displayName = meta.displayName();
        if (displayName == null || !"Buzul Kazma".equals(PLAIN_TEXT.serialize(displayName))) {
            return false;
        }
        List<Component> lore = meta.lore();
        return lore != null && lore.stream().map(PLAIN_TEXT::serialize)
                .anyMatch(line -> line.contains("Bu kazma buzları kırmana yarar."));
    }

    private ItemStack createGlacierPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(GLACIER_NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.empty(),
                LEGACY.deserialize(GLACIER_LORE).decoration(TextDecoration.ITALIC, false), Component.empty()));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(specialToolKey, PersistentDataType.STRING,
                ToolKind.GLACIER_PICKAXE.canonicalId);
        item.setItemMeta(meta);
        return item;
    }

    private BlockRule ruleFor(Block block) {
        for (BlockRule rule : rules.values()) {
            if (rule.world().equals(block.getWorld().getName()) && rule.materials().contains(block.getType())) {
                return rule;
            }
        }
        return null;
    }

    private static boolean closeTo(double left, double right) {
        return Math.abs(left - right) <= 1.0E-9;
    }

    private enum ToolKind {
        NONE("none", Material.AIR),
        WITCH_SHEARS("witch_shears", Material.SHEARS),
        ZEUS_HOE("zeus_hoe", Material.DIAMOND_HOE),
        GLACIER_PICKAXE("glacier_pickaxe", Material.DIAMOND_PICKAXE);

        private final String canonicalId;
        private final Material material;

        ToolKind(String canonicalId, Material material) {
            this.canonicalId = canonicalId;
            this.material = material;
        }

        private boolean matches(Material actual) {
            return this != NONE && material == actual;
        }

        private static ToolKind fromCanonicalId(String id) {
            if (id != null) {
                for (ToolKind kind : values()) {
                    if (kind.canonicalId.equals(id)) {
                        return kind;
                    }
                }
            }
            return NONE;
        }
    }

    private enum BlockRuleType { WITCH_WOOL, ATLANTIS_SPONGE, GLACIER_ICE }

    private record BlockRule(String world, Set<Material> materials, ToolKind required, boolean requiresFatigue) { }

    private record MiningAttempt(UUID world, int x, int y, int z, BlockRule rule, long startedAt) {
        private static MiningAttempt of(Block block, BlockRule rule, long tick) {
            return new MiningAttempt(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), rule, tick);
        }

        private boolean matches(Block block, BlockRule expected) {
            return world.equals(block.getWorld().getUID()) && x == block.getX() && y == block.getY()
                    && z == block.getZ() && rule == expected;
        }
    }
}
