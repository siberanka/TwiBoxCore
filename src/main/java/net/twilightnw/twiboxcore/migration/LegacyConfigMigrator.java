package net.twilightnw.twiboxcore.migration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.logging.Level;
import net.twilightnw.twiboxcore.TwiBoxCore;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Versioned, idempotent import of recognized project-owned legacy configs. */
public final class LegacyConfigMigrator {
    static final String COMBAT_MIGRATION_ID = "twilight-legacy-combat-config-v1";
    private static final String LEGACY_COMBAT_DIRECTORY = "TwilightLegacyCombatCompat";

    private LegacyConfigMigrator() {
    }

    public static Result run(TwiBoxCore plugin) {
        if (!plugin.getConfig().getBoolean("migration.import-legacy-configs", true)) {
            return new Result("disabled", false);
        }
        Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path pluginsDirectory = dataDirectory.getParent();
        if (pluginsDirectory == null) {
            plugin.getLogger().severe("Legacy config import skipped: plugin directory could not be resolved.");
            return new Result("failed", false);
        }
        Path statePath = dataDirectory.resolve("migration-state.yml");
        YamlConfiguration state = new YamlConfiguration();
        try {
            Files.createDirectories(dataDirectory);
            if (Files.isRegularFile(statePath)) {
                state.load(statePath.toFile());
            }
            if (state.getBoolean("completed." + COMBAT_MIGRATION_ID + ".done", false)) {
                return new Result("completed", false);
            }

            Path sourcePath = pluginsDirectory.resolve(LEGACY_COMBAT_DIRECTORY)
                    .resolve("config.yml").toAbsolutePath().normalize();
            if (!sourcePath.startsWith(pluginsDirectory.toAbsolutePath().normalize())
                    || !Files.isRegularFile(sourcePath)) {
                return new Result("pending-no-source", false);
            }

            YamlConfiguration source = loadYaml(sourcePath.toFile());
            YamlConfiguration defaults = loadBundledDefaults(plugin);
            String sourceHash = sha256(sourcePath);
            backupSource(dataDirectory, sourcePath, sourceHash);

            LegacyCombatConfigMapping.MappingResult mapping =
                    LegacyCombatConfigMapping.apply(source, plugin.getConfig(), defaults);
            if (mapping.changed()) {
                saveConfigurationAtomically(plugin.getConfig(), dataDirectory.resolve("config.yml"));
            }

            String stateRoot = "completed." + COMBAT_MIGRATION_ID;
            state.set(stateRoot + ".done", true);
            state.set(stateRoot + ".completed-at", Instant.now().toString());
            state.set(stateRoot + ".source", LEGACY_COMBAT_DIRECTORY + "/config.yml");
            state.set(stateRoot + ".source-sha256", sourceHash);
            state.set(stateRoot + ".imported-keys", mapping.imported());
            state.set(stateRoot + ".preserved-target-keys", mapping.preserved());
            saveConfigurationAtomically(state, statePath);

            plugin.getLogger().info("LEGACY_CONFIG_IMPORT completed source=" + LEGACY_COMBAT_DIRECTORY
                    + "/config.yml imported=" + mapping.imported().size()
                    + " preserved=" + mapping.preserved().size());
            return new Result("completed", mapping.changed());
        } catch (IOException | InvalidConfigurationException | NoSuchAlgorithmException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Legacy config import failed safely; no completion marker was written.", exception);
            return new Result("failed", false);
        }
    }

    private static YamlConfiguration loadYaml(File file)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        return configuration;
    }

    private static YamlConfiguration loadBundledDefaults(TwiBoxCore plugin) throws IOException {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                throw new IOException("Bundled config.yml is missing");
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static void backupSource(Path dataDirectory, Path source, String hash) throws IOException {
        Path backupDirectory = dataDirectory.resolve("migration-backups");
        Files.createDirectories(backupDirectory);
        String shortHash = hash.substring(0, 12);
        Path backup = backupDirectory.resolve(LEGACY_COMBAT_DIRECTORY + "-config-" + shortHash + ".yml");
        if (!Files.exists(backup)) {
            Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void saveConfigurationAtomically(FileConfiguration configuration, Path target)
            throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null) {
            throw new IOException("Configuration target has no parent: " + normalizedTarget);
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve(normalizedTarget.getFileName() + ".migration.tmp");
        configuration.save(temporary.toFile());
        try {
            Files.move(temporary, normalizedTarget,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record Result(String status, boolean configChanged) {
    }
}
