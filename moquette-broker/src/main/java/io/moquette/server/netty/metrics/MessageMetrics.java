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

package io.moquette.server.netty.metrics;

/**
 * 消息度量指标
 */
public class MessageMetrics {
    /**
     * 读消息的数量
     */
    private long messagesRead;
    /**
     * 写消息的数量
     */
    private long messageWrote;

    void incrementRead(long numMessages) {
        messagesRead += numMessages;
    }

    void incrementWrote(long numMessages) {
        messageWrote += numMessages;
    }

    public long messagesRead() {
        return messagesRead;
    }

    public long messagesWrote() {
        return messageWrote;
    }
}
