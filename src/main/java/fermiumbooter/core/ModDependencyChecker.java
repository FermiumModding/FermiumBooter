package fermiumbooter.core;

import fermiumbooter.api.CompatHandling;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Arrays;
import java.util.Optional;

/**
 * Checks mod dependencies and version requirements.
 */
public class ModDependencyChecker {

    public static boolean testDependency(CompatHandling dependency) {
        String modid = dependency.modid();
        String modName = dependency.modName();
        String versionRange = dependency.versionRange();

        // Find the mod
        Optional<? extends IModInfo> modInfo = LoadingModList.get().getMods().stream()
                .filter(info -> info.getModId().equals(modid))
                .findFirst();

        if (modInfo.isEmpty()) {
            FermiumBooterPlugin.LOGGER.debug("Mod dependency not satisfied: {} not found", modid);
            return false;
        }

        IModInfo mod = modInfo.get();

        // Check mod name if specified
        if (!modName.isEmpty() && !mod.getDisplayName().equals(modName)) {
            FermiumBooterPlugin.LOGGER.debug("Mod dependency not satisfied: {} found but name doesn't match (expected: {}, got: {})",
                    modid, modName, mod.getDisplayName());
            return false;
        }

        // Check version range if specified
        if (!versionRange.isEmpty()) {
            try {
                VersionRange range = VersionRange.createFromVersionSpec(versionRange);
                ArtifactVersion modVersion = new DefaultArtifactVersion(mod.getVersion().toString());

                if (!range.containsVersion(modVersion)) {
                    FermiumBooterPlugin.LOGGER.debug("Mod dependency not satisfied: {} version {} not in range {}",
                            modid, modVersion, versionRange);
                    return false;
                }
            } catch (Exception e) {
                FermiumBooterPlugin.LOGGER.error("Invalid version range '{}' for mod {}", versionRange, modid, e);
                return false;
            }
        }

        FermiumBooterPlugin.LOGGER.debug("Mod dependency satisfied: {} ({})", modid, mod.getVersion());
        return true;
    }

    public static boolean checkAllDependencies(CompatHandling[] dependencies) {
        return Arrays.stream(dependencies).allMatch(ModDependencyChecker::testDependency);
    }
}