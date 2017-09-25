package io.moquette.persistence.redis;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.moquette.server.config.IConfig;
import io.moquette.spi.IMessagesStore;
import io.moquette.spi.ISessionsStore;
import io.moquette.spi.IStore;

public class RedisStorageService implements IStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisStorageService.class);

    private IMessagesStore m_messageStore;
    private ISessionsStore m_sessionsStore;

    private RedisDao<?> redisDao = new RedisDao<>();

    public RedisStorageService(IConfig config) {

        Properties props = new Properties();

        props.put(RedisConstant.HOST, config.getProperty(RedisConstant.HOST));

        props.put(RedisConstant.PORT, config.getProperty(RedisConstant.PORT));

        props.put(RedisConstant.PASSWORD, config.getProperty(RedisConstant.PASSWORD));

        props.put(RedisConstant.DATABASE, config.getProperty(RedisConstant.DATABASE));

        props.put(RedisConstant.PREFIX, config.getProperty(RedisConstant.PREFIX));

        redisDao.connect(props);
        
        initStore();
    }

    @Override
    public void initStore() {

        m_messageStore = new RedisMessagesStore(redisDao);
        m_messageStore.initStore();

        m_sessionsStore = new RedisSessionStore(redisDao);
        m_sessionsStore.initStore();
    }

    @Override
    public void close() {

        LOG.info("close redis connection");

        redisDao.close();

        LOG.info("close redis successful");
    }

    @Override
    public IMessagesStore messagesStore() {
        return m_messageStore;
    }

    @Override
    public ISessionsStore sessionsStore() {
        return m_sessionsStore;
    }

}
