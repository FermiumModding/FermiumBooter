package fermiumbooter.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines a mod dependency for a conditional mixin.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ModDependency {
    /**
     * The mod ID that must be present.
     */
    String modid();

    /**
     * Optional mod name for additional filtering.
     * Useful when multiple mods share the same modid.
     * If empty, only modid is checked.
     */
    String modName() default "";

    /**
     * Version range in Maven version range format.
     * Examples: "[11.0,)", "[1.0,2.0)", "[1.5.0]"
     * Empty string means any version.
     */
    String versionRange() default "";
}