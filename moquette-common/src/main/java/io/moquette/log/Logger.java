package io.moquette.log;

import java.util.function.Supplier;

/**
 * 自定义logger
 */
public class Logger {

    private org.slf4j.Logger logger;

    Logger(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    public void error(Supplier<String> s, Throwable e) {
        if (logger.isErrorEnabled()) {
            logger.error(s.get(), e);
        }
    }

    public void error(Supplier<String> s, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(s.get(), args);
        }
    }

    public void error(Supplier<String> s) {
        if (logger.isErrorEnabled()) {
            logger.error(s.get());
        }
    }

    public void warn(Supplier<String> s) {
        if (logger.isWarnEnabled()) {
            logger.info(s.get());
        }
    }

    public void warn(Supplier<String> s, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(s.get(), args);
        }
    }

    public void info(Supplier<String> s) {
        if (logger.isInfoEnabled()) {
            logger.info(s.get());
        }
    }

    public void info(Supplier<String> s, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(s.get(), args);
        }
    }

    public void trace(Supplier<String> s) {
        if (logger.isTraceEnabled()) {
            logger.trace(s.get());
        }
    }

    public void trace(Supplier<String> s, Object... args) {
        if (logger.isTraceEnabled()) {
            logger.trace(s.get(), args);
        }
    }

    public void debug(Supplier<String> s) {
        if (logger.isDebugEnabled()) {
            logger.debug(s.get());
        }
    }

    public void debug(Supplier<String> s, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(s.get(), args);
        }
    }
}
