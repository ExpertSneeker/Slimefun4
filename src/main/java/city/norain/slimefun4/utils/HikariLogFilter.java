package city.norain.slimefun4.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class HikariLogFilter extends AbstractFilter {
    private final Level level;

    public HikariLogFilter(Level level) {
        this.level = level;
    }

    public static void registerFilter(Level level) {
        Logger logger = (Logger) LogManager.getRootLogger();
        logger.addFilter(new HikariLogFilter(level));
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) {
            return Result.NEUTRAL;
        }

        if (event.getLoggerName().contains("Hikari") && event.getLevel().isInRange(level, Level.FATAL)) {
            return Result.ACCEPT;
        }

        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return Result.NEUTRAL;
    }
}
