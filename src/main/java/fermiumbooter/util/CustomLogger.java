package fermiumbooter.util;

import fermiumbooter.config.FermiumBooterConfig;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.spongepowered.asm.mixin.throwables.MixinError;
import org.spongepowered.asm.mixin.throwables.MixinException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CustomLogger {

    public static final String LOGGER_NAME = "fermiumbooter";
    public static Path LOG_PATH;
    public static final Set<String> mixinErrors = Collections.synchronizedSet(new HashSet<>());

    public static void init() {
        LOG_PATH = Paths.get("logs", LOGGER_NAME + ".log");
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
                        Throwable cause = getCause(event.getThrown());
                        if(cause == null && event.getMessage().getFormattedMessage().startsWith("FermiumMixinConfig config")) return event.getMessage().getFormattedMessage().replaceFirst("FermiumMixinConfig config", "Fermium Config Warning:" + System.lineSeparator() + "\t") + System.lineSeparator();
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
        
        ctx.updateLoggers();
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