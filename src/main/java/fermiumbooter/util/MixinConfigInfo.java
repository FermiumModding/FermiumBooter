package fermiumbooter.util;

import java.util.ArrayList;
import java.util.List;

public class MixinConfigInfo {
    public final String modId;
    public final String name;
    public final String earlyJson;
    public final String lateJson;
    public final boolean defaultValue;

    public final List<CompatInfo> compatInfos = new ArrayList<>();

    public MixinConfigInfo(String modId, String name, String early, String late, boolean defaultValue) {
        this.modId = modId;
        this.name = name;
        this.earlyJson = early;
        this.lateJson = late;
        this.defaultValue = defaultValue;
    }

    public static class CompatInfo {
        public final String modId;
        public final String modName;
        public final String modVersionRange;
        public final boolean desired;
        public final boolean disableMixin;
        public final boolean warnIngame;
        public final String reason;

        public CompatInfo(String modId, String modName, String modVersionRange, boolean desired, boolean disableMixin, boolean warnIngame, String reason) {
            this.modId = modId;
            this.modName = modName;
            this.modVersionRange = modVersionRange;
            this.desired = desired;
            this.disableMixin = disableMixin;
            this.warnIngame = warnIngame;
            this.reason = reason;
        }
    }
}
