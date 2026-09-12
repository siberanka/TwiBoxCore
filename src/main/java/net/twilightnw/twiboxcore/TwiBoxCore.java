package net.twilightnw.twiboxcore;

import java.util.Locale;
import net.twilightnw.twiboxcore.combat.LegacyCombatModule;
import net.twilightnw.twiboxcore.equipment.EquipmentEffectsModule;
import net.twilightnw.twiboxcore.migration.LegacyConfigMigrator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TwiBoxCore extends JavaPlugin {
    private EquipmentEffectsModule equipmentEffects;
    private LegacyCombatModule legacyCombat;
    private String migrationStatus = "not-run";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LegacyConfigMigrator.Result migration = LegacyConfigMigrator.run(this);
        migrationStatus = migration.status();
        if (migration.configChanged()) {
            reloadConfig();
        }
        startModules();
        getLogger().info("TwiBoxCore " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        stopModules();
    }

    private void startModules() {
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
            case "selftest" -> {
                boolean equipmentPassed = equipmentEffects == null || equipmentEffects.selfTest(sender);
                boolean combatPassed = legacyCombat == null || legacyCombat.selfTest(sender);
                sender.sendMessage("TwiBoxCore SELFTEST=" + (equipmentPassed && combatPassed ? "PASS" : "FAIL"));
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
