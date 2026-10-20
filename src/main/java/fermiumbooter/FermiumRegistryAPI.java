package fermiumbooter;

import fermiumbooter.util.FermiumJarScanner;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Enqueue mixins to be applied or rejected from your IFMLLoadingPlugin class init
 * Includes options for disabling the mixin from a Supplier, and loading it either early or late
 * Since 1.6.0, early mixins can also target any mod's classes, as long as "Add All Mods To Classpath Early" is enabled (default), allowing this toggle to always be on false
 * Early suppliers run before mods are loaded, use FermiumRegistryAPI.isModPresent instead of Loader.isModLoaded there
 * Configuration name is the name of the json pointing to your mixin, such as "mixins.fermiumbooter.init.json"
 * For additional Forge @Config based mixin handling:
 * @see fermiumbooter.annotations.MixinConfig
 */
public abstract class FermiumRegistryAPI {

    private static final Logger LOGGER = LogManager.getLogger("FermiumRegistryAPI");

    private static HashMap<String, List<Supplier<Boolean>>> earlyMixins = new HashMap<>();
    private static HashMap<String, List<Supplier<Boolean>>> lateMixins = new HashMap<>();
    private static List<String> rejectMixins = new ArrayList<>();

    /**
     * Register multiple mixin config resources at once to be applied
     * Enqueue mixins from your IFMLLoadingPlugin class init
     * @param late - whether to apply the mixin late or early
     * @param configurations - mixin config resource names
     */
    public static void enqueueMixin(boolean late, String... configurations) {
        for(String configuration : configurations) {
            enqueueMixin(late, configuration);
        }
    }

    /**
     * Register a mixin config resource to be applied
     * Enqueue mixins from your IFMLLoadingPlugin class init
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     */
    public static void enqueueMixin(boolean late, String configuration) {
        enqueueMixin(late, configuration, true);
    }

    /**
     * Add a mixin config resource to be applied, with a toggle to apply or not
     * Note: I do not think this specific method is necessary, but it's here just in case
     * Enqueue mixins from your IFMLLoadingPlugin class init
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     * @param enabled - whether to apply the mixin or not
     */
    public static void enqueueMixin(boolean late, String configuration, boolean enabled) {
        enqueueMixin(late, configuration, () -> enabled);
    }

    /**
     * Add a mixin config resource to be applied, with a supplier to toggle application to be evaluated after all like-timed configs are registered
     * Note: If multiple suppliers are given for a single configuration, it is evaluated as OR
     * Enqueue mixins from your IFMLLoadingPlugin class init
     * @param late - whether to apply the mixin late or early
     * @param configuration - mixin config resource name
     * @param supplier - supplier to determine whether to apply the mixin or not
     */
    public static void enqueueMixin(boolean late, String configuration, Supplier<Boolean> supplier) {
        if(configuration == null || configuration.trim().isEmpty()) {
            LOGGER.log(Level.ERROR, "FermiumRegistryAPI supplied null or empty configuration name during mixin enqueue, ignoring.");
            return;
        }
        if(supplier == null) {//Do not evaluate supplier.get() itself for null now
            LOGGER.log(Level.ERROR, "FermiumRegistryAPI supplied null supplier for configuration \"{}\" during mixin enqueue, ignoring.", configuration);
            return;
        }
        //Process rejects prior to application
        if(late) {
            LOGGER.log(Level.INFO, "FermiumRegistryAPI supplied \"{}\" for late mixin enqueue, adding.", configuration);
            lateMixins.computeIfAbsent(configuration, k -> new ArrayList<>());
            lateMixins.get(configuration).add(supplier);
        }
        else {
            LOGGER.log(Level.INFO, "FermiumRegistryAPI supplied \"{}\" for early mixin enqueue, adding.", configuration);
            earlyMixins.computeIfAbsent(configuration, k -> new ArrayList<>());
            earlyMixins.get(configuration).add(supplier);
        }
    }

    /**
     * Designates a mixin config resource name to be ignored before application (Will only affect FermiumBooter applied mixins)
     * Note: Realistically you should not use this, but it is provided in the case of specific tweaker mod needs
     * Enqueue mixin removal from your IFMLLoadingPlugin class init
     * @param configuration - mixin config resource name
     */
    public static void removeMixin(String configuration) {
        if(configuration == null || configuration.trim().isEmpty()) {
            LOGGER.log(Level.ERROR, "FermiumRegistryAPI supplied null or empty configuration name for mixin removal, ignoring.");
            return;
        }
        LOGGER.log(Level.INFO, "FermiumRegistryAPI supplied \"{}\" for mixin removal, adding.", configuration);
        rejectMixins.add(configuration);
    }
    
    /**
     * Utility method to check if a mod is present similar to Loader.isModLoaded(ModID)
     * Typically Loader.isModLoaded(ModID) is used, however it is not populated when early mixins are applied
     * Use this method instead for the purposes of checking if a mixin should be applied based on if mods are present
     * @param modID the modID of the target mod to be checked if present
     * @return true if the provided modID is detected as present
     */
    public static boolean isModPresent(String modID) {
        return FermiumJarScanner.isModPresent(modID);
    }

    /**
     * Utility method to check if a present mod's version is in a given range, usable when early mixins are applied
     * @param modID the modID of the target mod
     * @param versionRange version range in the same format as @Mod(dependencies=...), e.g. "[1.2,2.0)", or an exact version
     * @return true if the mod is present and its version is in the range, false if it is absent, has no known version or the range is invalid
     */
    public static boolean isModVersionInRange(String modID, String versionRange) {
        return FermiumJarScanner.isModVersionInRange(modID, versionRange);
    }

    /**
     * Utility method to check if a present mod has a given name, to distinguish between forks using the same modID, usable when early mixins are applied
     * @param modID the modID of the target mod
     * @param modName the mod name to compare to, preferring the @Mod name over the mcmod.info name if both are present but not equal
     * @return true if the mod is present and has exactly the given name
     */
    public static boolean isModNamed(String modID, String modName) {
        return FermiumJarScanner.isModNamed(modID, modName);
    }

    /**
     * Utility method combining isModPresent, isModNamed and isModVersionInRange, mirroring the parameters of @MixinConfig.CompatHandling
     * @param modID the modID of the target mod
     * @param modName if not null, the mod also has to have exactly this name
     * @param versionRange if not null, the mod version also has to be in this range (an invalid range never matches)
     * @return true if the mod is present and matches the given name and version range
     */
    public static boolean isModPresent(String modID, String modName, String versionRange) {
        return FermiumJarScanner.isModPresent(modID, modName, versionRange);
    }

    /**
     * Internal Use; Do Not Use
     */
    public static HashMap<String, List<Supplier<Boolean>>> getEarlyMixins() {
        return earlyMixins;
    }

    /**
     * Internal Use; Do Not Use
     */
    public static HashMap<String, List<Supplier<Boolean>>> getLateMixins() {
        return lateMixins;
    }

    /**
     * Internal Use; Do Not Use
     */
    public static List<String> getRejectMixins() {
        return rejectMixins;
    }

    /**
     * Internal Use; Do Not Use
     */
    public static void clear() {
        // :)
        earlyMixins = null;
        lateMixins = null;
        rejectMixins = null;
    }
}