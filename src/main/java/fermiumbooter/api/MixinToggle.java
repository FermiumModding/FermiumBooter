package fermiumbooter.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines conditions for loading a mixin JSON file.
 * Place this annotation on config fields in a {@link MixinConfig} annotated class.
 *
 * <p>The mixin JSON will only be loaded if:
 * <ul>
 *   <li>All required mods are present (if specified)</li>
 *   <li>The config value matches the enable condition</li>
 *   <li>The side requirement from the mixin JSON is met</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @ConditionalMixin(
 *     mixinJson = "mixins.mymod.jei.json",
 *     requireMods = {@ModDependency(modid = "jei", versionRange = "[11.0,)")},
 *     disableWhen = "-1"
 * )
 * public static int jeiFeatureDistance = 5;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MixinToggle {
    /**
     * The mixin JSON file name (relative to resources root).
     * Example: "mixins.mymod.feature.json"
     */
    String mixinJson();

    /**
     * Required mod dependencies for this mixin to load.
     */
    CompatHandling[] requireMods() default {};

    /**
     * For boolean fields: if true, the mixin loads when config is true (default behavior).
     * For other types: use {@link #disableWhen()} instead.
     */
    boolean enableWhen() default true;

    /**
     * Value that disables the mixin. Works for all primitive types and Strings.
     * For int: "0" or "-1"
     * For String: "disabled"
     * For boolean: use {@link #enableWhen()} instead
     *
     * If empty, mixin is disabled only when boolean is false.
     */
    String disableWhen() default "";

    /**
     * Action to take when the mixin cannot be loaded.
     */
    FailureAction onFailure() default FailureAction.WARN;

    /**
     * Custom message to display on failure.
     * If empty, a default message will be generated.
     */
    String failureMessage() default "";

    /**
     * Description/comment to write in the config file.
     * If empty, no comment will be added.
     */
    String description() default "";
}