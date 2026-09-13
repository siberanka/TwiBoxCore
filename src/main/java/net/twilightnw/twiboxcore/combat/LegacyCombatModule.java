package net.twilightnw.twiboxcore.combat;

import com.google.common.collect.Multimap;
import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.offers.TradeOffer;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/** Bounded legacy-combat repair and explicit Shopkeepers item synchronization. */
public final class LegacyCombatModule implements Listener {
    private static final double EPSILON = 1.0E-9;

    private final TwiBoxCore plugin;
    private final NamespacedKey repairedKey =
            new NamespacedKey("twilightlegacycombatcompat", "repaired_schema");
    private boolean repairEnabled;
    private int minimumProtectionLevel;
    private boolean scalingEnabled;
    private ProtectionScaling scaling;
    private List<ItemStack> canonicalTradeEquipment = List.of();
    private long repairedItems;
    private long canonicalizedItems;
    private long removedLegacyMarkers;

    public LegacyCombatModule(TwiBoxCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshCanonicalCatalog();
        plugin.getLogger().info("COMBAT_READY repair=" + repairEnabled
                + " protectionScaling=" + scalingEnabled
                + " inventoryScan=manual canonicalCatalog=" + canonicalTradeEquipment.size());
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        canonicalTradeEquipment = List.of();
    }

    public String status() {
        return "active(repair=" + repairEnabled + ",scaling=" + scalingEnabled
                + ",scan=manual,repaired=" + repairedItems
                + ",canonicalized=" + canonicalizedItems
                + ",removedLegacyMarkers=" + removedLegacyMarkers
                + ",catalog=" + canonicalTradeEquipment.size() + ")";
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

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!scalingEnabled || !(event.getEntity() instanceof Player player)) {
            return;
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

    public void scanNow(CommandSender sender) {
        int changed = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            changed += repairPlayer(player);
        }
        sender.sendMessage("TwiBoxCore inventory scan complete: changed=" + changed
                + ", online=" + plugin.getServer().getOnlinePlayers().size());
    }

    private int repairPlayer(Player player) {
        int changed = 0;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] inventoryItems = inventory.getContents();
        int inventoryCount = repairArray(inventoryItems);
        if (inventoryCount > 0) {
            inventory.setContents(inventoryItems);
            changed += inventoryCount;
        }
        ItemStack[] enderItems = player.getEnderChest().getContents();
        int enderCount = repairArray(enderItems);
        if (enderCount > 0) {
            player.getEnderChest().setContents(enderItems);
            changed += enderCount;
        }
        if (changed > 0) {
            plugin.getLogger().info("Synchronized " + changed + " item(s) for " + player.getName());
        }
        return changed;
    }

    private int repairArray(ItemStack[] items) {
        int changedItems = 0;
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            boolean changed = repairItem(item);
            ItemStack canonical = canonicalReplacement(item);
            if (canonical != null) {
                items[index] = canonical;
                canonicalizedItems++;
                changed = true;
            }
            if (changed) {
                changedItems++;
            }
        }
        return changedItems;
    }

    private boolean repairItem(ItemStack item) {
        if (!repairEnabled || item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (meta.getPersistentDataContainer().has(repairedKey)) {
            meta.getPersistentDataContainer().remove(repairedKey);
            removedLegacyMarkers++;
            changed = true;
        }
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            if (changed) {
                item.setItemMeta(meta);
                repairedItems++;
            }
            return changed;
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
            if (changed) {
                item.setItemMeta(meta);
                repairedItems++;
            }
            return changed;
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
        item.setItemMeta(meta);
        repairedItems++;
        return true;
    }

    public int refreshCanonicalCatalog() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Shopkeepers")) {
            canonicalTradeEquipment = List.of();
            plugin.getLogger().warning("Shopkeepers is unavailable; canonical synchronization is disabled.");
            return 0;
        }
        if (!ShopkeepersAPI.isEnabled()) {
            canonicalTradeEquipment = List.of();
            plugin.getLogger().warning("Shopkeepers API is not ready; canonical synchronization is disabled.");
            return 0;
        }
        List<ItemStack> catalog = new ArrayList<>();
        for (Shopkeeper shopkeeper : ShopkeepersAPI.getShopkeeperRegistry().getAllShopkeepers()) {
            if (!(shopkeeper instanceof RegularAdminShopkeeper adminShopkeeper)) {
                continue;
            }
            for (TradeOffer offer : adminShopkeeper.getOffers()) {
                addCanonical(catalog, offer.getResultItem());
                addCanonical(catalog, offer.getItem1());
                if (offer.hasItem2()) {
                    addCanonical(catalog, offer.getItem2());
                }
            }
        }
        canonicalTradeEquipment = List.copyOf(catalog);
        plugin.getLogger().info("CATALOG_READY items=" + catalog.size());
        return catalog.size();
    }

    private void addCanonical(List<ItemStack> catalog, UnmodifiableItemStack source) {
        if (source == null) {
            return;
        }
        ItemStack item = source.copy();
        if (!isEquipment(item.getType()) || !item.hasItemMeta()) {
            return;
        }
        item.setAmount(1);
        for (ItemStack existing : catalog) {
            if (existing.isSimilar(item)) {
                return;
            }
        }
        catalog.add(item);
    }

    private ItemStack canonicalReplacement(ItemStack source) {
        if (source == null || source.getType().isAir() || !source.hasItemMeta()
                || !isEquipment(source.getType()) || canonicalTradeEquipment.isEmpty()) {
            return null;
        }
        ItemStack selected = null;
        ItemStack normalizedSource = comparisonCopy(source);
        for (ItemStack candidate : canonicalTradeEquipment) {
            if (candidate.getType() != source.getType()) {
                continue;
            }
            if (candidate.isSimilar(source)) {
                return null;
            }
            if (!comparisonCopy(candidate).isSimilar(normalizedSource)) {
                continue;
            }
            if (selected != null && !selected.isSimilar(candidate)) {
                return null;
            }
            selected = candidate;
        }
        if (selected == null) {
            return null;
        }
        ItemStack replacement = selected.clone();
        replacement.setAmount(source.getAmount());
        return replacement;
    }

    private ItemStack comparisonCopy(ItemStack source) {
        ItemStack copy = source.clone();
        copy.setAmount(1);
        if (!copy.hasItemMeta()) {
            return copy;
        }
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(repairedKey);
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers != null && !modifiers.isEmpty()) {
            List<ModifierSpec> specs = modifiers.entries().stream()
                    .map(entry -> new ModifierSpec(entry.getKey(), entry.getValue().getAmount(),
                            entry.getValue().getOperation(), entry.getValue().getSlotGroup()))
                    .sorted(Comparator
                            .comparing((ModifierSpec spec) -> spec.attribute().getKey().toString())
                            .thenComparingDouble(ModifierSpec::amount)
                            .thenComparing(spec -> spec.operation().name())
                            .thenComparing(spec -> spec.slotGroup().toString()))
                    .toList();
            meta.setAttributeModifiers(null);
            for (int index = 0; index < specs.size(); index++) {
                ModifierSpec spec = specs.get(index);
                meta.addAttributeModifier(spec.attribute(), new AttributeModifier(
                        new NamespacedKey("twilightcompare", "modifier_" + index),
                        spec.amount(), spec.operation(), spec.slotGroup()));
            }
        }
        copy.setItemMeta(meta);
        return copy;
    }

    public int exportCanonicalCatalog() throws IOException {
        Path exportDirectory = plugin.getDataFolder().toPath().resolve("canonical-export");
        Files.createDirectories(exportDirectory);
        try (var paths = Files.list(exportDirectory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString()
                    .matches("canonical-[0-9]{5}\\.nbt")).toList()) {
                Files.delete(path);
            }
        }
        List<String> manifest = new ArrayList<>();
        for (int index = 0; index < canonicalTradeEquipment.size(); index++) {
            ItemStack item = canonicalTradeEquipment.get(index);
            String fileName = String.format(Locale.ROOT, "canonical-%05d.nbt", index);
            Files.write(exportDirectory.resolve(fileName), item.serializeAsBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            manifest.add(index + "\t" + item.getType().getKey() + "\t" + fileName);
        }
        Files.write(exportDirectory.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return canonicalTradeEquipment.size();
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
            case "refresh" -> {
                sender.sendMessage("CATALOG_REFRESH=PASS items=" + refreshCanonicalCatalog());
                yield true;
            }
            case "export" -> {
                try {
                    sender.sendMessage("CATALOG_EXPORT=PASS items=" + exportCanonicalCatalog());
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not export canonical catalog", exception);
                    sender.sendMessage("CATALOG_EXPORT=FAIL");
                }
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
            require(!helmet.getItemMeta().getPersistentDataContainer().has(repairedKey),
                    "a gameplay bookkeeping marker was persisted");
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
            require(previous <= scaling.maximumExtraReduction() + EPSILON,
                    "protection scaling exceeds cap");
            require(runCanonicalComparisonSelfTest(), "canonical modifier-ID comparison failed");
            sender.sendMessage("Combat self-test: PASS");
            return true;
        } catch (IllegalStateException exception) {
            sender.sendMessage("Combat self-test: FAIL (" + exception.getMessage() + ")");
            plugin.getLogger().log(Level.SEVERE, "Combat self-test failed", exception);
            return false;
        }
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

    private boolean isEquipment(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || material == Material.SHEARS || material == Material.BOW || material == Material.CROSSBOW
                || material == Material.TRIDENT || material == Material.MACE || material == Material.SHIELD
                || material == Material.ELYTRA || material == Material.FISHING_ROD
                || material == Material.CARROT_ON_A_STICK || material == Material.WARPED_FUNGUS_ON_A_STICK
                || material == Material.BRUSH || material == Material.FLINT_AND_STEEL;
    }

    private void addDummyAttribute(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addAttributeModifier(Attribute.GRAVITY,
                new AttributeModifier(new NamespacedKey("playerkits2", "dummy_attribute"), 0.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        item.setItemMeta(meta);
    }

    private boolean runCanonicalComparisonSelfTest() {
        ItemStack first = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack second = first.clone();
        ItemMeta firstMeta = first.getItemMeta();
        ItemMeta secondMeta = second.getItemMeta();
        firstMeta.addEnchant(Enchantment.PROTECTION, 38, true);
        secondMeta.addEnchant(Enchantment.PROTECTION, 38, true);
        firstMeta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey("twilight", "selftest_first"), 12.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        secondMeta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey("twilight", "selftest_second"), 12.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        first.setItemMeta(firstMeta);
        second.setItemMeta(secondMeta);
        return !first.isSimilar(second) && comparisonCopy(first).isSimilar(comparisonCopy(second));
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

    private record ModifierSpec(Attribute attribute, double amount,
            AttributeModifier.Operation operation, EquipmentSlotGroup slotGroup) {
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
