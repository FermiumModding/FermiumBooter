package fermiumbooter.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class FermiumBooterPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("FermiumBooterPlugin");
    private FermiumJarScanner fermiumJarScanner;

    @Override
    public String getRefMapperConfig() {
        return "fermiumbooter.mixins.refmap.json";
    }

    @Override
    public List<String> getMixins() {
        // Load conditional mixins from @MixinConfig classes
        if (fermiumJarScanner != null)
            return fermiumJarScanner.getToggleMixins();
        return null;
    }

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Initializing JarScanner...");
        fermiumJarScanner = new FermiumJarScanner();
    }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return true;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
