package fermiumbooter.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for classes that contain conditional mixin toggles.
 * The booter will scan for all classes with this annotation during mixin loading phase.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @MixinConfig
 * public class MyModMixinToggles {
 *     @ConditionalMixin(mixinJson = "mixins.mymod.feature.json")
 *     public static boolean enableFeature = true;
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinConfig {
    /**
     * The config file name (without path or extension).
     * Defaults to the modid derived from package structure.
     * The final config will be at config/{value}-mixintoggles.toml
     */
    String fileName() default "";
}