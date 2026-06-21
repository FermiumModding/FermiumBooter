package fermiumbooter;

import fermiumbooter.api.ModDependency;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Optional;

/**
 * Checks mod dependencies and version requirements.
 */
public class ModDependencyChecker {
    private static final Logger LOGGER = LogManager.getLogger("FermiumBooter");

    /**
     * Checks if a mod dependency is satisfied.
     *
     * @param dependency The dependency to check
     * @return true if the dependency is satisfied
     */
    public boolean checkDependency(ModDependency dependency) {
        String modid = dependency.modid();
        String modName = dependency.modName();
        String versionRange = dependency.versionRange();

        // Find the mod
        Optional<? extends IModInfo> modInfo = LoadingModList.get().getMods().stream()
                .filter(info -> info.getModId().equals(modid))
                .findFirst();

        if (modInfo.isEmpty()) {
            LOGGER.debug("Mod dependency not satisfied: {} not found", modid);
            return false;
        }

        IModInfo mod = modInfo.get();

        // Check mod name if specified
        if (!modName.isEmpty() && !mod.getDisplayName().equals(modName)) {
            LOGGER.debug("Mod dependency not satisfied: {} found but name doesn't match (expected: {}, got: {})",
                    modid, modName, mod.getDisplayName());
            return false;
        }

        // Check version range if specified
        if (!versionRange.isEmpty()) {
            try {
                VersionRange range = VersionRange.createFromVersionSpec(versionRange);
                ArtifactVersion modVersion = new DefaultArtifactVersion(mod.getVersion().toString());

                if (!range.containsVersion(modVersion)) {
                    LOGGER.debug("Mod dependency not satisfied: {} version {} not in range {}",
                            modid, modVersion, versionRange);
                    return false;
                }
            } catch (Exception e) {
                LOGGER.error("Invalid version range '{}' for mod {}", versionRange, modid, e);
                return false;
            }
        }

        LOGGER.debug("Mod dependency satisfied: {} ({})", modid, mod.getVersion());
        return true;
    }

    /**
     * Checks if all dependencies in an array are satisfied.
     */
    public boolean checkAllDependencies(ModDependency[] dependencies) {
        for (ModDependency dep : dependencies) {
            if (!checkDependency(dep)) {
                return false;
            }
        }
        return true;
    }
}