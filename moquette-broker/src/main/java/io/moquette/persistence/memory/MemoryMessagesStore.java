/*
 * Copyright (c) 2012-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.persistence.memory;

import io.moquette.log.Logger;
import io.moquette.log.LoggerFactory;
import io.moquette.spi.IMatchingCondition;
import io.moquette.spi.IMessagesStore;
import io.moquette.spi.impl.subscriptions.Topic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存信息存储
 */
public class MemoryMessagesStore implements IMessagesStore {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryMessagesStore.class);

    /**
     * 保留的消息
     */
    private Map<Topic, StoredMessage> retainedStore = new ConcurrentHashMap<>();

    MemoryMessagesStore() {
    }

    @Override
    public void initStore() {
    }

    @Override
    public void storeRetained(Topic topic, StoredMessage storedMessage) {
        LOG.debug(() -> "Store retained message for topic={}, CId={}", topic, storedMessage.getClientID());

        if (storedMessage.getClientID() == null) {
            throw new IllegalArgumentException("Message to be persisted must have a not null client ID");
        }
        retainedStore.put(topic, storedMessage);
    }

    @Override
    public Collection<StoredMessage> searchMatching(IMatchingCondition condition) {
        LOG.debug(() -> "searchMatching scanning all retained messages, presents are {}", retainedStore.size());

        List<StoredMessage> results = new ArrayList<>();

        for (Topic topic : retainedStore.keySet()) {
            if (condition.match(topic)) {
                results.add(retainedStore.get(topic));
            }
        }
        return results;
    }

    @Override
    public void cleanRetained(Topic topic) {
        retainedStore.remove(topic);
    }
}
