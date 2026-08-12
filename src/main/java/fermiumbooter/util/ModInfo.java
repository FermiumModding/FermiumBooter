package fermiumbooter.util;

import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;

public class ModInfo {
    public final String modId;
    public String modName;
    public ArtifactVersion version;

    public ModInfo(String modid, String version, String name) {
        this.modId = modid;
        this.modName = name;
        setVersion(version);
    }

    public void setVersion(String version) {
        this.version = version == null ? null : new DefaultArtifactVersion(version);
    }
}
