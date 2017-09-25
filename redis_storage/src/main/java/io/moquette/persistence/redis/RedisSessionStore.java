package io.moquette.persistence.redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import io.moquette.persistence.PersistentSession;
import io.moquette.spi.ClientSession;
import io.moquette.spi.IMessagesStore.StoredMessage;
import io.moquette.spi.ISessionsStore;
import io.moquette.spi.ISubscriptionsStore;
import io.moquette.spi.impl.subscriptions.Subscription;
import io.moquette.spi.impl.subscriptions.Topic;

public class RedisSessionStore implements ISessionsStore, ISubscriptionsStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisSessionStore.class);

    private RedisDao<?> redisDao;

    private final String outboundFlightMessages = "outboundFlight:";

    private final String inboundInflight = "inboundInflight:";

    // map clientID <-> set of currently in flight
    // packet identifiers
    private final String m_inFlightIds = "inflightPacketIDs:";

    private final String m_persistentSessions = "sessions:";

    private final String clients = "clients";

    // maps clientID->[MessageId -> guid]
    private final String m_secondPhaseStore = "secondPhase:";

    private final String subscriptions = "subscriptions:";

    private final String topics = "topics:";

    private Map<String, BlockingQueue<StoredMessage>> blockingQueue = new HashMap<>();

    public RedisSessionStore(RedisDao<?> redisDao) {
        this.redisDao = redisDao;
    }

    @Override
    public void initStore() {
    }

    @Override
    public void addNewSubscription(Subscription newSubscription) {

        LOG.info("Adding new subscription. ClientId={}, topics={}", newSubscription.getClientId(), newSubscription.getTopicFilter());

        final String clientID = newSubscription.getClientId();
        String key = subscriptions + clientID;
        Topic topic = newSubscription.getTopicFilter();

        ValueOperations<String, Topic> topicDao = redisDao.opsForValue();

        redisDao.opsForHash().put(key, topic.toString(), newSubscription);
        topicDao.set(topics + topic.toString(), topic);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Subscription has been added. ClientId={}, topics={}, clientSubscriptions={}", newSubscription.getClientId(),
                    newSubscription.getTopicFilter(), redisDao.opsForHash().get(key, topic.toString()));
        }
    }

    @Override
    public void removeSubscription(Topic topic, String clientID) {
        LOG.info("Removing subscription. ClientId={}, topics={}", clientID, topic);
        if (!redisDao.opsForHash().hasKey(subscriptions + clientID, topic.toString())) {
            return;
        }
        redisDao.opsForHash().delete(subscriptions + clientID, topic.toString());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Subscription has been removed. ClientId={}, topics={}, clientSubscriptions={}", clientID, topic,
                    redisDao.opsForHash().values(subscriptions + clientID));
        }

    }

    @Override
    public void wipeSubscriptions(String clientID) {
        LOG.info("Wiping subscriptions. CId={}", clientID);
        redisDao.opsForHash().getOperations().delete(subscriptions + clientID);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Subscriptions have been removed. ClientId={}, clientSubscriptions={}", clientID,
                    redisDao.opsForHash().entries(subscriptions + clientID));
        }
    }

    @Override
    public List<ClientTopicCouple> listAllSubscriptions() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Retrieving existing subscriptions");
        }

        SetOperations<String, String> clientOpt = redisDao.opsForSet();

        HashOperations<String, String, Subscription> subOpt = redisDao.opsForHash();

        Set<String> clientSet = clientOpt.members(clients);

        ValueOperations<String, Topic> topicDao = redisDao.opsForValue();

        final List<ClientTopicCouple> allSubscriptions = new ArrayList<>();
        for (String clientID : clientSet) {
            Set<String> topicNames = subOpt.entries(subscriptions + clientID).keySet();
            for (String topicName : topicNames) {
                Topic topicFilter = topicDao.get(topics + topicName);
                allSubscriptions.add(new ClientTopicCouple(clientID + "", topicFilter));
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("The existing subscriptions have been retrieved. Result={}", allSubscriptions);
        }
        return allSubscriptions;
    }

    @Override
    public Subscription getSubscription(ClientTopicCouple couple) {
        HashOperations<String, String, Subscription> opt = redisDao.opsForHash();
        Map<String, Subscription> clientSubscriptions = opt.entries(subscriptions + couple.clientID);
        if (LOG.isDebugEnabled())
            LOG.debug("Retrieving subscriptions. CId={}, subscriptions={}", couple.clientID, clientSubscriptions);
        return clientSubscriptions.get(couple.topicFilter.toString());
    }

    @Override
    public List<Subscription> getSubscriptions() {
        if (LOG.isDebugEnabled())
            LOG.debug("Retrieving existing subscriptions...");

        List<Subscription> subscriptions = new ArrayList<>();

        SetOperations<String, String> clientOpt = redisDao.opsForSet();

        HashOperations<String, String, Subscription> subOpt = redisDao.opsForHash();

        Set<String> set = clientOpt.members(clients);

        for (String clientID : set) {
            Map<String, Subscription> clientSubscriptions = subOpt.entries(subscriptions + clientID);
            subscriptions.addAll(clientSubscriptions.values());
        }

        if (LOG.isDebugEnabled())
            LOG.debug("Existing subscriptions has been retrieved Result={}", subscriptions);

        return subscriptions;
    }

    @Override
    public ISubscriptionsStore subscriptionStore() {
        return this;
    }

    @Override
    public void updateCleanStatus(String clientID, boolean cleanSession) {
        LOG.info("Updating cleanSession flag. CId={}, cleanSession={}", clientID, cleanSession);

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();

        operation.set(m_persistentSessions + clientID, new PersistentSession(cleanSession));
    }

    @Override
    public boolean contains(String clientID) {
        return redisDao.opsForHash().size(subscriptions + clientID) > 0;
    }

    @Override
    public ClientSession createNewSession(String clientID, boolean cleanSession) {

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();

        if (operation.get(m_persistentSessions + clientID) != null) {
            LOG.error("Unable to create a new session: the client ID is already in use. ClientId={}, cleanSession={}", clientID, cleanSession);
            throw new IllegalArgumentException("Can't create a session with the ID of an already existing" + clientID);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Creating new session. CId={}, cleanSession={}", clientID, cleanSession);

        operation.setIfAbsent(m_persistentSessions + clientID, new PersistentSession(cleanSession));

        SetOperations<String, String> clientOpt = redisDao.opsForSet();
        clientOpt.add(clients, clientID);

        return new ClientSession(clientID, this, this, cleanSession);
    }

    @Override
    public ClientSession sessionForClient(String clientID) {
        if (LOG.isDebugEnabled())
            LOG.debug("Retrieving session CId={}", clientID);

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();

        PersistentSession storedSession = operation.get(m_persistentSessions + clientID);
        if (storedSession == null) {
            LOG.warn("Session does not exist CId={}", clientID);
            return null;
        }

        return new ClientSession(clientID, this, this, storedSession.cleanSession);
    }

    @Override
    public Collection<ClientSession> getAllSessions() {

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();
        SetOperations<String, String> clientOpt = redisDao.opsForSet();

        Set<String> clientsSet = clientOpt.members(clients);

        Collection<ClientSession> result = new ArrayList<>();

        for (String clientId : clientsSet) {
            PersistentSession session = operation.get(m_persistentSessions + clientId);
            result.add(new ClientSession(clientId, this, this, session.cleanSession));
        }

        return result;
    }

    @Override
    public StoredMessage inFlightAck(String clientID, int messageID) {
        if (LOG.isDebugEnabled())
            LOG.debug("Acknowledging inflight message CId={}, messageId={}", clientID, messageID);

        HashOperations<String, String, StoredMessage> operation = redisDao.opsForHash();

        StoredMessage msg = operation.get(outboundFlightMessages + clientID, String.valueOf(messageID));
        if (msg == null) {
            LOG.error("Can't find the inFlight record for client <{}> and for messageId <{}>", clientID, messageID);
            /***
             * throw new RuntimeException("Can't
             * find the inFlight record for client
             * <" + clientID + ">" + " messageId
             * for<" + messageID + ">");
             **/
        }
        operation.delete(outboundFlightMessages + clientID, String.valueOf(messageID));

        // remove from the ids store
        SetOperations<String, ?> flightOperation = redisDao.opsForSet();
        flightOperation.remove(m_inFlightIds + clientID, messageID);

        return msg;
    }

    @Override
    public void inFlight(String clientID, int messageID, StoredMessage msg) {
        HashOperations<String, String, StoredMessage> operation = redisDao.opsForHash();

        operation.put(outboundFlightMessages + clientID, String.valueOf(messageID), msg);
    }

    @Override
    public int nextPacketID(String clientID) {
        if (LOG.isDebugEnabled())
            LOG.debug("Generating next packet ID CId={}", clientID);

        SetOperations<String, Integer> flightOperation = redisDao.opsForSet();

        Set<Integer> set = flightOperation.members(m_inFlightIds + clientID);
        int nextPacketId = 1;

        if (set.size() != 0) {
            nextPacketId = (Collections.max(set) % 0xFFFF) + 1;
        }

        flightOperation.add(m_inFlightIds + clientID, nextPacketId);

        if (LOG.isDebugEnabled())
            LOG.debug("Next packet ID has been generated CId={}, result={}", clientID, nextPacketId);

        return nextPacketId;
    }

    @Override
    public BlockingQueue<StoredMessage> queue(String clientID) {
        LOG.info("Queuing pending message. ClientId={}, guid={}", clientID);
        if (blockingQueue.get(clientID) == null) {
            @SuppressWarnings("unchecked")
            RedisDao<StoredMessage> dao = (RedisDao<StoredMessage>) redisDao;
            BlockingQueue<StoredMessage> queue = new RedisQueue<>(dao, clientID);
            blockingQueue.put(clientID, queue);
        }
        return blockingQueue.get(clientID);
    }

    @Override
    public void dropQueue(String clientID) {
        LOG.info("Removing pending messages. ClientId={}", clientID);
        blockingQueue.remove(clientID);
    }

    @Override
    public void moveInFlightToSecondPhaseAckWaiting(String clientID, int messageID, StoredMessage msg) {
        if (LOG.isDebugEnabled())
            LOG.debug("Moving inflight message to 2nd phase ack state. ClientId={}, messageID={}", clientID, messageID);

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();

        PersistentSession p = operation.get(m_persistentSessions + clientID);
        if (p == null) {
            String error = String.format("Can't find the inFlight record for client <%s> during the second phase of " + "QoS2 pub", clientID);
            LOG.error(error);
            throw new RuntimeException(error);
        }

        HashOperations<String, String, StoredMessage> storedOperation = redisDao.opsForHash();

        storedOperation.put(outboundFlightMessages + clientID, String.valueOf(messageID), msg);

        storedOperation.put(m_secondPhaseStore + clientID, String.valueOf(messageID), msg);
    }

    @Override
    public StoredMessage secondPhaseAcknowledged(String clientID, int messageID) {
        if (LOG.isDebugEnabled())
            LOG.debug("Processing second phase ACK CId={}, messageId={}", clientID, messageID);

        ValueOperations<String, PersistentSession> operation = redisDao.opsForValue();

        PersistentSession p = operation.get(m_persistentSessions + clientID);
        if (p == null) {
            String error = String.format("Can't find the inFlight record for client <%s> during the second phase " + "acking of QoS2 pub", clientID);
            LOG.error(error);
            throw new RuntimeException(error);
        }

        HashOperations<String, Integer, StoredMessage> storedOperation = redisDao.opsForHash();

        StoredMessage msg = storedOperation.get(m_secondPhaseStore + clientID, messageID);
        storedOperation.delete(m_secondPhaseStore + clientID, messageID);

        return msg;
    }

    @Override
    public int getInflightMessagesNo(String clientID) {
        int totalInflight = 0;

        HashOperations<String, String, StoredMessage> storedOperation = redisDao.opsForHash();

        totalInflight += storedOperation.size((inboundInflight + clientID));

        totalInflight += storedOperation.size((m_secondPhaseStore + clientID));

        totalInflight += storedOperation.size((outboundFlightMessages + clientID));

        return totalInflight;
    }

    @Override
    public StoredMessage inboundInflight(String clientID, int messageID) {

        if (LOG.isDebugEnabled())
            LOG.debug("Mapping inbound message ID to GUID CId={}, messageId={}", clientID, messageID);

        HashOperations<String, String, StoredMessage> storedOperation = redisDao.opsForHash();

        StoredMessage msg = storedOperation.get(inboundInflight + clientID, String.valueOf(messageID));

        return msg;
    }

    @Override
    public void markAsInboundInflight(String clientID, int messageID, StoredMessage msg) {
        HashOperations<String, String, StoredMessage> storedOperation = redisDao.opsForHash();

        storedOperation.put(inboundInflight + clientID, String.valueOf(messageID), msg);
    }

    @Override
    public int getPendingPublishMessagesNo(String clientID) {
        return queue(clientID).size();
    }

    @Override
    public int getSecondPhaseAckPendingMessages(String clientID) {
        HashOperations<String, String, StoredMessage> storedOperation = redisDao.opsForHash();

        return storedOperation.size(m_secondPhaseStore).intValue();
    }

    @Override
    public void cleanSession(String clientID) {
        // remove also the messages stored of type
        // QoS1/2
        LOG.info("Removing stored messages with QoS 1 and 2. ClientId={}", clientID);

        ValueOperations<String, Object> storedOperation = redisDao.opsForValue();

        storedOperation.getOperations().delete(m_secondPhaseStore + clientID);
        storedOperation.getOperations().delete(outboundFlightMessages + clientID);
        storedOperation.getOperations().delete(m_inFlightIds + clientID);

        LOG.info("Wiping existing subscriptions. ClientId={}", clientID);
        wipeSubscriptions(clientID);

        // remove also the enqueued messages
        dropQueue(clientID);
    }
}
