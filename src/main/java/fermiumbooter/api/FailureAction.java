package fermiumbooter.api;

/**
 * Defines the action to take when a conditional mixin fails to load.
 */
public enum FailureAction {
    /**
     * Silently skip the mixin without any logging.
     */
    IGNORE,

    /**
     * Log a warning and skip the mixin.
     */
    WARN,

    /**
     * Throw an exception and crash the game.
     */
    ERROR
}