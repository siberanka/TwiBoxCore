package net.twilightnw.twiboxcore.combat;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.twilightnw.twiboxcore.TwiBoxCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Restores legacy OP armor attributes and protection scaling on modern Paper. */
public final class LegacyCombatModule implements Listener {
    private static final int SCHEMA_VERSION = 1;
    private static final double EPSILON = 1.0E-9;

    private final TwiBoxCore plugin;
    private final Set<UUID> queuedScans = new HashSet<>();
    private final NamespacedKey repairedKey = new NamespacedKey("twilightlegacycombatcompat", "repaired_schema");
    private boolean repairEnabled;
    private int minimumProtectionLevel;
    private boolean scalingEnabled;
    private ProtectionScaling scaling;
    private BukkitTask periodicTask;
    private long repairedItems;

    public LegacyCombatModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long interval = Math.max(20L, plugin.getConfig()
                .getLong("legacy-combat.repair.online-scan-interval-ticks", 100L));
        periodicTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::scanOnlinePlayers, 20L, interval);
        plugin.getLogger().info("COMBAT_READY repair=" + repairEnabled
                + " protectionScaling=" + scalingEnabled + " scanInterval=" + interval);
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        queuedScans.clear();
    }

    public String status() {
        return "active(repair=" + repairEnabled + ",scaling=" + scalingEnabled
                + ",repaired=" + repairedItems + ")";
    }

    private void loadSettings() {
        String root = "legacy-combat.";
        repairEnabled = plugin.getConfig().getBoolean(root + "repair.enabled", true);
        minimumProtectionLevel = Math.max(1,
                plugin.getConfig().getInt(root + "repair.minimum-protection-level", 15));
        scalingEnabled = plugin.getConfig().getBoolean(root + "protection-scaling.enabled", true);
        scaling = new ProtectionScaling(
                plugin.getConfig().getInt(root + "protection-scaling.vanilla-total-level-cap", 20),
                plugin.getConfig().getDouble(root + "protection-scaling.extra-reduction-per-excess-level", 0.0022),
                plugin.getConfig().getDouble(root + "protection-scaling.maximum-extra-reduction", 0.35));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        queueScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queueScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queueScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            queueScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        queueScan(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        queueScan(event.getPlayer());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!scalingEnabled || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (repairEnabled) {
            repairPlayer(player);
        }
        double extraReduction = scaling.extraReduction(totalProtectionLevel(player));
        if (extraReduction <= 0.0 || !event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
            return;
        }
        double finalDamage = event.getFinalDamage();
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0) {
            return;
        }
        try {
            double currentMagicModifier = event.getDamage(EntityDamageEvent.DamageModifier.MAGIC);
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC,
                    currentMagicModifier - (finalDamage * extraReduction));
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            plugin.getLogger().log(Level.FINE, "Could not apply proportional protection modifier", exception);
        }
    }

    private void queueScan(Player player) {
        if (!repairEnabled || !queuedScans.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            queuedScans.remove(player.getUniqueId());
            if (player.isOnline()) {
                repairPlayer(player);
            }
        });
    }

    private void scanOnlinePlayers() {
        if (repairEnabled) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                repairPlayer(player);
            }
        }
    }

    public void scanNow(CommandSender sender) {
        int repaired = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            repaired += repairPlayer(player);
        }
        sender.sendMessage("Scan complete: repaired=" + repaired);
    }

    private int repairPlayer(Player player) {
        int repaired = 0;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] inventoryItems = inventory.getContents();
        int inventoryCount = repairArray(inventoryItems);
        if (inventoryCount > 0) {
            inventory.setContents(inventoryItems);
            repaired += inventoryCount;
        }
        ItemStack[] enderItems = player.getEnderChest().getContents();
        int enderCount = repairArray(enderItems);
        if (enderCount > 0) {
            player.getEnderChest().setContents(enderItems);
            repaired += enderCount;
        }
        if (repaired > 0) {
            repairedItems += repaired;
            plugin.getLogger().info("Repaired " + repaired + " legacy combat item(s) for " + player.getName());
        }
        return repaired;
    }

    private int repairArray(ItemStack[] items) {
        int repaired = 0;
        for (ItemStack item : items) {
            if (repairItem(item)) {
                repaired++;
            }
        }
        return repaired;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasAttributeModifiers()) {
            return false;
        }
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        List<Map.Entry<Attribute, AttributeModifier>> dummyEntries = new ArrayList<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (entry.getKey().equals(Attribute.GRAVITY)
                    && Math.abs(modifier.getAmount()) <= EPSILON
                    && modifier.getKey().getKey().contains("dummy_attribute")) {
                dummyEntries.add(entry);
            }
        }
        if (dummyEntries.isEmpty()) {
            return false;
        }
        boolean dummyOnly = dummyEntries.size() == modifiers.size();
        for (Map.Entry<Attribute, AttributeModifier> entry : dummyEntries) {
            meta.removeAttributeModifier(entry.getKey(), entry.getValue());
        }
        int protectionLevel = meta.getEnchantLevel(Enchantment.PROTECTION);
        ArmorSlot armorSlot = ArmorSlot.from(item.getType());
        if (dummyOnly && armorSlot != null && protectionLevel >= minimumProtectionLevel) {
            meta.setAttributeModifiers(null);
            addCanonicalArmorModifiers(meta, armorSlot, protectionLevel);
        } else if (dummyOnly) {
            meta.setAttributeModifiers(null);
        }
        meta.getPersistentDataContainer().set(repairedKey, PersistentDataType.INTEGER, SCHEMA_VERSION);
        item.setItemMeta(meta);
        return true;
    }

    private void addCanonicalArmorModifiers(ItemMeta meta, ArmorSlot slot, int protectionLevel) {
        LegacyArmorValues values = LegacyArmorValues.calculate(slot.piece, protectionLevel);
        meta.addAttributeModifier(Attribute.ARMOR,
                modifier("legacy_armor_" + slot.key, values.armor(), slot.group));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                modifier("legacy_toughness_" + slot.key, values.toughness(), slot.group));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                modifier("legacy_knockback_" + slot.key, values.knockbackResistance(), slot.group));
    }

    private AttributeModifier modifier(String key, double amount, EquipmentSlotGroup slot) {
        return new AttributeModifier(new NamespacedKey("twilight", key), amount,
                AttributeModifier.Operation.ADD_NUMBER, slot);
    }

    private int totalProtectionLevel(Player player) {
        int total = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                total += armor.getEnchantmentLevel(Enchantment.PROTECTION);
            }
        }
        return total;
    }

    public boolean handleCommand(CommandSender sender, String[] args) {
        String subcommand = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "status" -> {
                sender.sendMessage("TwiBoxCore legacy combat: " + status());
                yield true;
            }
            case "scan" -> {
                scanNow(sender);
                yield true;
            }
            case "selftest" -> {
                sender.sendMessage("SELFTEST=" + (selfTest(sender) ? "PASS" : "FAIL"));
                yield true;
            }
            default -> false;
        };
    }

    public boolean selfTest(CommandSender sender) {
        try {
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 38);
            addDummyAttribute(helmet);
            require(repairItem(helmet), "helmet was not selected for repair");
            require(attributeAmount(helmet, Attribute.ARMOR) == 8.0, "Prot 38 helmet armor is not 8");
            require(attributeAmount(helmet, Attribute.ARMOR_TOUGHNESS) == 3.0,
                    "Prot 38 helmet toughness is not 3");
            require(attributeAmount(helmet, Attribute.KNOCKBACK_RESISTANCE) == 0.1,
                    "Prot 38 helmet knockback resistance is not 0.1");
            require(attributeCount(helmet, Attribute.GRAVITY) == 0, "dummy gravity modifier remains");

            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 27);
            addDummyAttribute(sword);
            require(repairItem(sword), "sword was not selected for repair");
            require(!sword.getItemMeta().hasAttributeModifiers(), "sword defaults remain suppressed");
            require(sword.getEnchantmentLevel(Enchantment.SHARPNESS) == 27, "unsafe sharpness changed");

            int[] totals = {20, 60, 80, 92, 112, 132, 152, 180};
            double previous = -1.0;
            for (int total : totals) {
                double reduction = scaling.extraReduction(total);
                require(reduction + EPSILON >= previous, "protection scaling is not monotonic");
                previous = reduction;
            }
            require(previous <= scaling.maximumExtraReduction() + EPSILON, "protection scaling exceeds cap");
            sender.sendMessage("Combat self-test: PASS");
            return true;
        } catch (IllegalStateException exception) {
            sender.sendMessage("Combat self-test: FAIL (" + exception.getMessage() + ")");
            plugin.getLogger().log(Level.SEVERE, "Combat self-test failed", exception);
            return false;
        }
    }

    private void addDummyAttribute(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addAttributeModifier(Attribute.GRAVITY,
                new AttributeModifier(new NamespacedKey("playerkits2", "dummy_attribute"), 0.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        item.setItemMeta(meta);
    }

    private double attributeAmount(ItemStack item, Attribute attribute) {
        Collection<AttributeModifier> modifiers = item.getItemMeta().getAttributeModifiers(attribute);
        return modifiers == null ? 0.0 : modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
    }

    private int attributeCount(ItemStack item, Attribute attribute) {
        Collection<AttributeModifier> modifiers = item.getItemMeta().getAttributeModifiers(attribute);
        return modifiers == null ? 0 : modifiers.size();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private enum ArmorSlot {
        HEAD("head", EquipmentSlotGroup.HEAD, LegacyArmorValues.Piece.HEAD),
        CHEST("chest", EquipmentSlotGroup.CHEST, LegacyArmorValues.Piece.CHEST),
        LEGS("legs", EquipmentSlotGroup.LEGS, LegacyArmorValues.Piece.LEGS),
        FEET("feet", EquipmentSlotGroup.FEET, LegacyArmorValues.Piece.FEET);

        private final String key;
        private final EquipmentSlotGroup group;
        private final LegacyArmorValues.Piece piece;

        ArmorSlot(String key, EquipmentSlotGroup group, LegacyArmorValues.Piece piece) {
            this.key = key;
            this.group = group;
            this.piece = piece;
        }

        private static ArmorSlot from(Material material) {
            String name = material.name();
            if (name.endsWith("_HELMET") || material == Material.PLAYER_HEAD) {
                return HEAD;
            }
            if (name.endsWith("_CHESTPLATE")) {
                return CHEST;
            }
            if (name.endsWith("_LEGGINGS")) {
                return LEGS;
            }
            if (name.endsWith("_BOOTS")) {
                return FEET;
            }
            return null;
        }
    }
}
