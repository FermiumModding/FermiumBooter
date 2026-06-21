package fermiumbooter.core;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads config files early during mixin loading phase using NightConfig directly.
 * This bypasses Forge's config system which is not yet available during IMixinConfigPlugin execution.
 */
public class EarlyConfigReader {
    private static final Logger LOGGER = LogManager.getLogger("FermiumBooter");
    private final Map<String, CommentedFileConfig> configCache = new HashMap<>();

    /**
     * Loads a config file from the config directory.
     * Creates the file with default values if it doesn't exist.
     *
     * @param configPath Path to the config file (relative to config directory)
     * @param defaults Default values to write if file doesn't exist
     * @param comments Comments to write for each config entry
     * @return The loaded config
     */
    public CommentedFileConfig loadConfig(Path configPath, Map<String, Object> defaults, Map<String, String> comments) {
        String key = configPath.toString();

        if (configCache.containsKey(key)) {
            return configCache.get(key);
        }

        CommentedFileConfig config = CommentedFileConfig.builder(configPath)
                .build();

        // Create with defaults if it doesn't exist
        if (!Files.exists(configPath)) {
            LOGGER.info("Creating new mixin toggle config: {}", configPath);
            try {
                Files.createDirectories(configPath.getParent());

                // Write defaults
                for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                    String k = entry.getKey();
                    config.set(k, entry.getValue());

                    // Add comment if available
                    if (comments.containsKey(k) && !comments.get(k).isEmpty()) {
                        config.setComment(k, comments.get(k));
                    }
                }

                config.save();
                LOGGER.info("Created config file with {} default value(s): {}", defaults.size(), configPath);
            } catch (IOException e) {
                LOGGER.error("Failed to create config file: {}", configPath, e);
            }
        } else {
            config.load();
            LOGGER.debug("Loaded existing config: {}", configPath);
        }

        configCache.put(key, config);
        return config;
    }

    /**
     * Gets a value from the config, returning the default if not found.
     */
    public <T> T getValue(CommentedFileConfig config, String path, T defaultValue) {
        if (config.contains(path)) {
            return config.get(path);
        }
        return defaultValue;
    }

    /**
     * Checks if a config value matches the disable condition.
     *
     * @param configValue The actual value from config
     * @param disableWhen The value that should disable (as string)
     * @return true if the mixin should be disabled
     */
    public boolean matchesDisableCondition(Object configValue, String disableWhen) {
        if (disableWhen.isEmpty()) {
            // For boolean, disable when false
            if (configValue instanceof Boolean) {
                return !(Boolean) configValue;
            }
            return false;
        }

        // Convert both to string and compare
        String valueStr = String.valueOf(configValue);
        return valueStr.equals(disableWhen);
    }

    /**
     * Evaluates if a mixin should be enabled based on config value and conditions.
     *
     * @param configValue The actual config value
     * @param enableWhen For boolean configs: mixin enabled when config == enableWhen
     * @param disableWhen String value that disables the mixin
     * @return true if mixin should be loaded
     */
    public boolean evaluateConfigCondition(Object configValue, boolean enableWhen, String disableWhen) {
        // Check disable condition first
        if (matchesDisableCondition(configValue, disableWhen)) {
            return false;
        }

        // For booleans, check enableWhen
        if (configValue instanceof Boolean) {
            return ((Boolean) configValue) == enableWhen;
        }

        // For other types, as long as it doesn't match disableWhen, it's enabled
        return true;
    }

    /**
     * Closes all cached configs.
     */
    public void close() {
        configCache.values().forEach(CommentedFileConfig::close);
        configCache.clear();
    }
}