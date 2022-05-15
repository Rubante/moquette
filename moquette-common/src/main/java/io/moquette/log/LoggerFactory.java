package io.moquette.log;

public class LoggerFactory {
    private LoggerFactory() {
    }

    public static io.moquette.log.Logger getLogger(Class<?> cls) {
        return new io.moquette.log.Logger(org.slf4j.LoggerFactory.getLogger(cls));
    }
}
