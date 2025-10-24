package fermiumbooter.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class CustomLogger {

    public static final String LOGGER_NAME = "fermiumbooter";
    public static final Logger LOGGER = LogManager.getLogger(LOGGER_NAME);
    public static Path LOG_PATH;

    public static void init() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();


        LOG_PATH = Paths.get(Launch.minecraftHome.getPath(), "logs", LOGGER_NAME+".log");
        try { Files.createDirectories(LOG_PATH.getParent()); } catch (IOException ignored) {}

        // Layout matching the vanilla log style
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%msg%n")
                .withConfiguration(config)
                .build();

        // append = false → truncates the file each startup
        FileAppender appender = FileAppender.newBuilder()
                .withFileName(LOG_PATH.toString())
                .setName(LOGGER_NAME)
                .withAppend(false)
                .withImmediateFlush(true)
                .withBufferedIo(true)
                .withBufferSize(8192)
                .setLayout(layout)
                .setConfiguration(config)
                .build();

        appender.start();
        config.addAppender(appender);

        LoggerConfig loggerCfg = new LoggerConfig(LOGGER_NAME, Level.DEBUG, true);
        loggerCfg.addAppender(appender, Level.DEBUG, null);
        loggerCfg.setAdditive(true);

        LoggerConfig old = config.getLoggerConfig(LOGGER_NAME);
        if (old != null && old != config.getRootLogger()) {
            config.removeLogger(LOGGER_NAME);
        }

        config.addLogger(LOGGER_NAME, loggerCfg);
        ctx.updateLoggers();
    }
}
