package io.moquette.persistence.redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;

import io.moquette.spi.IMatchingCondition;
import io.moquette.spi.IMessagesStore;
import io.moquette.spi.impl.subscriptions.Topic;

public class RedisMessagesStore implements IMessagesStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisMessagesStore.class);

    private RedisDao<?> redisDao;

    private final String m_retainedStore = "m_retainedStore";

    public RedisMessagesStore(RedisDao<?> redisDao) {
        this.redisDao = redisDao;
    }

    @Override
    public void initStore() {
    }

    @Override
    public Collection<StoredMessage> searchMatching(IMatchingCondition condition) {

        if (LOG.isDebugEnabled())
            LOG.debug("Scanning retained messages");

        List<StoredMessage> results = new ArrayList<>();

        HashOperations<String, Topic, StoredMessage> operation = redisDao.opsForHash();
        Set<Topic> topics = operation.keys(m_retainedStore);

        for (Topic topic : topics) {

            if (condition.match(topic)) {
                StoredMessage storedMsg = operation.get(m_retainedStore, topic);
                results.add(storedMsg);
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Retained messages have been scanned matchingMessages={}", results);
        }

        return results;

    }

    @Override
    public void cleanRetained(Topic topic) {
        HashOperations<String, Topic, StoredMessage> operation = redisDao.opsForHash();

        operation.delete(m_retainedStore, topic);
    }

    @Override
    public void storeRetained(Topic topic, StoredMessage storedMessage) {
        if (LOG.isDebugEnabled())
            LOG.debug("Store retained message for topic={}, CId={}", topic, storedMessage.getClientID());

        if (storedMessage.getClientID() == null) {
            throw new IllegalArgumentException("Message to be persisted must have a not null client ID");
        }

        HashOperations<String, Topic, StoredMessage> operation = redisDao.opsForHash();
        operation.put(m_retainedStore, topic, storedMessage);
    }

}
