package net.twilightnw.twiboxcore;

import java.util.Locale;
import net.twilightnw.twiboxcore.combat.LegacyCombatModule;
import net.twilightnw.twiboxcore.equipment.EquipmentEffectsModule;
import net.twilightnw.twiboxcore.migration.LegacyConfigMigrator;
import net.twilightnw.twiboxcore.shopbridge.ShopkeepersFancyNpcsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TwiBoxCore extends JavaPlugin {
    private EquipmentEffectsModule equipmentEffects;
    private LegacyCombatModule legacyCombat;
    private ShopkeepersFancyNpcsModule shopBridge;
    private String migrationStatus = "not-run";

    @Override
    public void onLoad() {
        shopBridge = new ShopkeepersFancyNpcsModule(this);
        shopBridge.load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        materializeShopBridgeDefaults();
        LegacyConfigMigrator.Result migration = LegacyConfigMigrator.run(this);
        migrationStatus = migration.status();
        if (migration.configChanged()) {
            reloadConfig();
        }
        startModules();
        getLogger().info("TwiBoxCore " + getPluginMeta().getVersion() + " enabled.");
    }

    private void materializeShopBridgeDefaults() {
        boolean changed = false;
        changed |= setIfMissing("modules.shopkeepers-fancynpcs", true);
        changed |= setIfMissing("shopkeepers-fancynpcs.admin-edit.enabled", true);
        changed |= setIfMissing("shopkeepers-fancynpcs.admin-edit.max-distance", 8.0);
        changed |= setIfMissing("shopkeepers-fancynpcs.admin-edit.cooldown-ticks", 10L);
        changed |= setIfMissing("shopkeepers-fancynpcs.admin-edit.required-permissions", java.util.List.of(
                "fancynpcs.command.npc.action.add", "shopkeeper.admin", "shopkeeper.remoteedit"));
        if (getConfig().getInt("config-version", 0) < 5) {
            getConfig().set("config-version", 5);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info("Materialized Shopkeepers/FancyNpcs settings without changing existing values.");
        }
    }

    private boolean setIfMissing(String path, Object value) {
        if (getConfig().isSet(path)) {
            return false;
        }
        getConfig().set(path, value);
        return true;
    }

    @Override
    public void onDisable() {
        stopModules();
    }

    private void startModules() {
        if (getConfig().getBoolean("modules.shopkeepers-fancynpcs", true)) {
            if (shopBridge == null) {
                shopBridge = new ShopkeepersFancyNpcsModule(this);
                shopBridge.load();
            }
            shopBridge.enable();
        }
        if (getConfig().getBoolean("modules.equipment-effects", true)) {
            equipmentEffects = new EquipmentEffectsModule(this);
            equipmentEffects.enable();
        }
        if (getConfig().getBoolean("modules.legacy-combat", true)) {
            legacyCombat = new LegacyCombatModule(this);
            legacyCombat.enable();
        }
    }

    private void stopModules() {
        if (shopBridge != null) {
            shopBridge.disable();
        }
        if (equipmentEffects != null) {
            equipmentEffects.disable();
            equipmentEffects = null;
        }
        if (legacyCombat != null) {
            legacyCombat.disable();
            legacyCombat = null;
        }
        getServer().getScheduler().cancelTasks(this);
    }

    private void reloadCore() {
        stopModules();
        reloadConfig();
        startModules();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "twilighttool" -> equipmentEffects != null && equipmentEffects.handleToolCommand(sender, args);
            case "twilightcombat" -> legacyCombat != null && legacyCombat.handleCommand(sender, args);
            case "twiboxcore" -> handleCoreCommand(sender, args);
            default -> false;
        };
    }

    private boolean handleCoreCommand(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> {
                sender.sendMessage("TwiBoxCore " + getPluginMeta().getVersion()
                        + ": equipment=" + (equipmentEffects == null ? "disabled" : equipmentEffects.status())
                        + ", combat=" + (legacyCombat == null ? "disabled" : legacyCombat.status())
                        + ", shopBridge=" + (shopBridge == null ? "disabled" : shopBridge.status())
                        + ", legacyImport=" + migrationStatus);
                return true;
            }
            case "reload" -> {
                reloadCore();
                sender.sendMessage("TwiBoxCore configuration and modules reloaded.");
                return true;
            }
            case "scan" -> {
                if (legacyCombat == null) {
                    sender.sendMessage("Legacy combat module is disabled.");
                } else {
                    legacyCombat.scanNow(sender);
                }
                return true;
            }
            case "refresh" -> {
                if (legacyCombat == null) {
                    sender.sendMessage("Legacy combat module is disabled.");
                } else {
                    sender.sendMessage("CATALOG_REFRESH=PASS items="
                            + legacyCombat.refreshCanonicalCatalog());
                }
                return true;
            }
            case "export" -> {
                if (legacyCombat == null) {
                    sender.sendMessage("Legacy combat module is disabled.");
                } else {
                    try {
                        sender.sendMessage("CATALOG_EXPORT=PASS items="
                                + legacyCombat.exportCanonicalCatalog());
                    } catch (java.io.IOException exception) {
                        getLogger().log(java.util.logging.Level.SEVERE,
                                "Could not export canonical catalog", exception);
                        sender.sendMessage("CATALOG_EXPORT=FAIL");
                    }
                }
                return true;
            }
            case "selftest" -> {
                boolean equipmentPassed = equipmentEffects == null || equipmentEffects.selfTest(sender);
                boolean combatPassed = legacyCombat == null || legacyCombat.selfTest(sender);
                boolean bridgePassed = shopBridge == null || shopBridge.selfTest(sender);
                sender.sendMessage("TwiBoxCore SELFTEST="
                        + (equipmentPassed && combatPassed && bridgePassed ? "PASS" : "FAIL"));
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
