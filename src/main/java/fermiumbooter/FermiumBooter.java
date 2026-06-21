package fermiumbooter;

import com.mojang.logging.LogUtils;
import fermiumbooter.api.config.Config;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(FermiumBooter.MODID)
public class FermiumBooter {
    public static final String MODID = "fermiumbooter";
    public static IEventBus MOD_EVENT_BUS;
    public static final Logger LOGGER = LogUtils.getLogger();

    public FermiumBooter(FMLJavaModLoadingContext context) {
        MOD_EVENT_BUS = context.getModEventBus();

        MOD_EVENT_BUS.register(Config.class);
    }
}
