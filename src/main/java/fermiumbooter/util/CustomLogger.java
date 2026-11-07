package fermiumbooter.util;

import fermiumbooter.config.FermiumBooterConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.spongepowered.asm.mixin.throwables.MixinError;
import org.spongepowered.asm.mixin.throwables.MixinException;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CustomLogger {

    public static final String LOGGER_NAME = "fermiumbooter";
    public static Path LOG_PATH;
    public static final Marker INGAME_WARNING = MarkerManager.getMarker("FERMIUM_INGAME_WARNING");
    public static final Set<String> mixinErrors = Collections.synchronizedSet(new HashSet<>());
    //Loggers under this prefix only write to fermiumbooter.log, used for info logs when "Log Mixin Info Only To FermiumBooter Log" is enabled
    private static final String FILE_ONLY_PREFIX = LOGGER_NAME + ".fileonly";
    private static boolean infoOnlyToFile = false;

    public static void init() {
        if(LOG_PATH != null) return;
        LOG_PATH = Paths.get("logs", LOGGER_NAME + ".log");
        //Can run before FermiumPlugin's static init (other coremods using FermiumRegistryAPI), so read the config directly
        infoOnlyToFile = new net.minecraftforge.common.config.Configuration(new File("config", "fermiumbooter.cfg")).get("general", "Log Info Only To FB Log", false).getBoolean();
        try { Files.createDirectories(LOG_PATH.getParent()); }
        catch(IOException ignored) {}
        
        LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        FileAppender appender = FileAppender.newBuilder()
                .withFileName(LOG_PATH.toString())
                .setName(LOGGER_NAME)
                .withAppend(false)
                .setLayout(new AbstractStringLayout(Charset.defaultCharset()) {
                    @Override
                    public String toSerializable(LogEvent event) {
                        if(event.getLoggerName().startsWith(FILE_ONLY_PREFIX + ".")) {
                            return "[" + event.getLoggerName().substring(FILE_ONLY_PREFIX.length() + 1) + "/" + event.getLevel() + "] " + event.getMessage().getFormattedMessage() + System.lineSeparator();
                        }
                        Throwable cause = getCause(event.getThrown());
                        if(cause == null && event.getMessage().getFormattedMessage().startsWith("FermiumMixinConfig config")) {
                            String prefix = INGAME_WARNING.equals(event.getMarker()) ? "Fermium Config Warning:" : "Fermium Config Note:";
                            return event.getMessage().getFormattedMessage().replaceFirst("FermiumMixinConfig config", prefix + System.lineSeparator() + "\t") + System.lineSeparator();
                        }
                        if(cause instanceof MixinException || cause instanceof MixinError) {
                            String simpleMessage = cause.getClass().getSimpleName() + ":" + System.lineSeparator() + "\t" + cause.getMessage().split("[\\r\\n]")[0] + System.lineSeparator();
                            if(FermiumBooterConfig.appendGeneralMixinExceptionsToCrashReports) mixinErrors.add(simpleMessage);
                            return "General Mixin Error " + simpleMessage;
                        }
                        return "Unhandled Error:" + System.lineSeparator() + "\t" + event.getMessage().getFormattedMessage() + System.lineSeparator();
                    }
                })
                .setConfiguration(config)
                .build();

        appender.start();
        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, Level.WARN, new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if(event.getMessage() == null) return Result.DENY;
                Throwable cause = getCause(event.getThrown());
                if(cause == null) return event.getMessage().getFormattedMessage().startsWith("FermiumMixinConfig config") ? Result.ACCEPT : Result.DENY;
                return(cause instanceof MixinException || cause instanceof MixinError) ? Result.ACCEPT : Result.DENY;
            }
        });

        if(infoOnlyToFile) {
            LoggerConfig fileOnlyConfig = new LoggerConfig(FILE_ONLY_PREFIX, Level.ALL, false);
            fileOnlyConfig.addAppender(appender, null, null);
            config.addLogger(FILE_ONLY_PREFIX, fileOnlyConfig);
        }
        
        ctx.updateLoggers();
    }

    public static void info(Logger logger, String message, Object... params) {
        init();
        if(infoOnlyToFile) LogManager.getLogger(FILE_ONLY_PREFIX + "." + logger.getName()).info(message, params);
        else logger.info(message, params);
    }
    
    @Nullable
    private static Throwable getCause(Throwable throwable) {
        if(throwable == null) return null;
        Set<Throwable> seen = new HashSet<>();
        Throwable cause;
        while((cause = throwable.getCause()) != null && seen.add(cause)) {
            throwable = cause;
        }
        return throwable;
    }
}