package fermiumbooter.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class FermiumBooterPlugin implements IMixinConnector {
    public static final Logger LOGGER = LogManager.getLogger("FermiumBooterPlugin");

    @Override
    public void connect() {
        for(String jsonFileName : FermiumJarScanner.getToggleMixins())
            Mixins.addConfiguration(jsonFileName);
    }
}
