package fermiumbooter.util;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.spongepowered.asm.mixin.throwables.MixinError;
import org.spongepowered.asm.mixin.throwables.MixinException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CustomLogger {

    public static final String LOGGER_NAME = "fermiumbooter";
    public static final Logger LOGGER = LogManager.getLogger(LOGGER_NAME);
    public static Path LOG_PATH;

    public static void init() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        LOG_PATH = Paths.get(Launch.minecraftHome.getPath(), "logs", LOGGER_NAME + ".log");
        try { Files.createDirectories(LOG_PATH.getParent()); } catch (IOException ignored) {}

        FileAppender appender = FileAppender.newBuilder()
                .withFileName(LOG_PATH.toString())
                .setName(LOGGER_NAME)
                .withAppend(false)
                .withImmediateFlush(true)
                .withBufferedIo(true)
                .withBufferSize(8192)
                .setLayout(new AbstractStringLayout(Charset.defaultCharset()) {
                    @Override
                    public String toSerializable(LogEvent event) {
                        // janky but works as we only catch actual errors or compat config warnings.
                        // needs to be better if there should be more stuff to log to the custom file
                        if(event.getThrown() == null) return event.getMessage().getFormattedMessage().replaceFirst("FermiumMixinConfig config", "Mixin Compatibility Config:") + System.lineSeparator();
                        Throwable t = event.getThrown();
                        while (t.getCause() != null) t = t.getCause(); //root cause
                        return "Unhandled Mixin Error: "+System.lineSeparator() + "\t" + t.getClass().getSimpleName() + " " + t.getMessage().split("[\\r\\n]")[0] + System.lineSeparator();
                    }
                })
                .setConfiguration(config)
                .build();

        appender.start();
        config.addAppender(appender);

        config.getRootLogger().addAppender(appender, Level.ERROR, new AbstractFilter(){
            @Override public Result filter(LogEvent event) {
                if(event.getMessage() == null) return Result.DENY;
                if(event.getMessage().getFormattedMessage().startsWith("FermiumMixinConfig")) return Result.ACCEPT;

                Throwable t = event.getThrown();
                if(t == null) return Result.DENY;
                while(t.getCause() != null) t = t.getCause();   //Root cause
                return (t instanceof MixinException || t instanceof MixinError) ? Result.ACCEPT : Result.DENY;
            }
        });

        ctx.updateLoggers();
    }
}