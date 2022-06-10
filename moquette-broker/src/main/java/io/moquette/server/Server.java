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

package io.moquette.server;

import io.moquette.BrokerConstants;
import io.moquette.connections.IConnectionsManager;
import io.moquette.interception.InterceptHandler;
import io.moquette.log.Logger;
import io.moquette.log.LoggerFactory;
import io.moquette.server.config.*;
import io.moquette.server.netty.NettyAcceptor;
import io.moquette.spi.impl.ProtocolProcessor;
import io.moquette.spi.impl.ProtocolProcessorBootstrapper;
import io.moquette.spi.impl.subscriptions.Subscription;
import io.moquette.spi.security.IAuthenticator;
import io.moquette.spi.security.IAuthorizator;
import io.moquette.spi.security.ISslContextCreator;
import io.netty.handler.codec.mqtt.MqttPublishMessage;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.moquette.logging.LoggingUtils.getInterceptorIds;

/**
 * Launch a configured version of the server.
 */
public class Server {

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private ServerAcceptor m_acceptor;

    /**
     * 是否已经完成初始化
     */
    private volatile boolean initialized;

    // 协议处理器
    private ProtocolProcessor processor;

    // 协议处理器加载器
    private ProtocolProcessorBootstrapper processorBootstrapper;

    // 调度执行服务
    private ScheduledExecutorService scheduler;

    public static void main(String[] args) throws IOException {
        final Server server = new Server();
        server.startServer();
        LOG.info(() -> "Server started, version 0.10.8-SNAPSHOT");
        // Bind a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stopServer()));
    }

    /**
     * 启动Moquette
     * 使用m_config/moquette.conf的配置信息
     *
     * @throws IOException IO异常.
     */
    public void startServer() throws IOException {

        // 获取默认配置文件路径
        File defaultConfigurationFile = defaultConfigFile();

        LOG.info(() -> "Starting Moquette server. Configuration file path={}", defaultConfigurationFile.getAbsolutePath());

        IResourceLoader filesystemLoader = new FileResourceLoader(defaultConfigurationFile);
        IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
    }

    /**
     * 默认配置文件，使用系统变量moquette.path获取配置文件的目录，目录默认为null
     *
     * @return
     */
    private static File defaultConfigFile() {
        String configPath = System.getProperty("moquette.path", null);
        return new File(configPath, IConfig.DEFAULT_CONFIG);
    }

    /**
     * Starts Moquette bringing the configuration
     * from the given file
     *
     * @param configFile text file that contains the
     *                   configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(File configFile) throws IOException {
        LOG.info(() -> "Starting Moquette server. Configuration file path={}", configFile.getAbsolutePath());

        IResourceLoader filesystemLoader = new FileResourceLoader(configFile);
        IConfig config = new ResourceLoaderConfig(filesystemLoader);
        startServer(config);
    }

    /**
     * Starts the server with the given
     * properties.
     * <p>
     * Its suggested to at least have the
     * following properties:
     * <ul>
     * <li>port</li>
     * <li>password_file</li>
     * </ul>
     *
     * @param configProps the properties map to use as
     *                    configuration.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(Properties configProps) throws IOException {
        LOG.info(() -> "Starting Moquette server using properties object");
        final IConfig config = new MemoryConfig(configProps);
        startServer(config);
    }

    /**
     * Starts Moquette bringing the configuration
     * files from the given Config implementation.
     *
     * @param config the configuration to use to
     *               start the broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config) throws IOException {
        LOG.info(() -> "Starting Moquette server using IConfig instance...");
        startServer(config, null);
    }

    /**
     * Starts Moquette with config provided by an
     * implementation of IConfig class and with
     * the set of InterceptHandler.
     *
     * @param config   the configuration to use to
     *                 start the broker.
     * @param handlers the handlers to install in the
     *                 broker.
     * @throws IOException in case of any IO Error.
     */
    public void startServer(IConfig config, List<? extends InterceptHandler> handlers) throws IOException {
        LOG.info(() -> "Starting moquette server using IConfig instance and intercept handlers");
        startServer(config, handlers, null, null, null);
    }

    public void startServer(IConfig config, List<? extends InterceptHandler> handlers, ISslContextCreator sslCtxCreator, IAuthenticator authenticator, IAuthorizator authorizator)
            throws IOException {
        if (handlers == null) {
            handlers = Collections.emptyList();
        }
        LOG.info(() -> "Starting Moquette Server. MQTT message interceptors={}", getInterceptorIds(handlers));

        scheduler = Executors.newScheduledThreadPool(1);

        String handlerProp = System.getProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME);
        if (handlerProp != null) {
            config.setProperty(BrokerConstants.INTERCEPT_HANDLER_PROPERTY_NAME, handlerProp);
        }

        String persistencePath = config.getProperty(BrokerConstants.PERSISTENT_STORE_PROPERTY_NAME);
        LOG.info(() -> "Configuring Using persistent store file, path={}", persistencePath);
        processorBootstrapper = new ProtocolProcessorBootstrapper();
        ProtocolProcessor processor = processorBootstrapper.init(config, handlers, authenticator, authorizator, this);
        LOG.debug(() -> "Initialized MQTT protocol processor");
        if (sslCtxCreator == null) {
            LOG.info(() -> "Using default SSL context creator");
            sslCtxCreator = new DefaultMoquetteSslContextCreator(config);
        }

        LOG.info(() -> "Binding server to the configured ports");
        m_acceptor = new NettyAcceptor();
        m_acceptor.initialize(processor, config, sslCtxCreator);
        this.processor = processor;

        LOG.info(() -> "Moquette server has been initialized successfully");
        initialized = true;
    }

    /**
     * Use the broker to publish a message. It's
     * intended for embedding applications. It can
     * be used only after the server is correctly
     * started with startServer.
     *
     * @param msg      the message to forward.
     * @param clientId the id of the sending server.
     * @throws IllegalStateException if the server is not yet
     *                               started
     */
    public void internalPublish(MqttPublishMessage msg, final String clientId) {
        final int messageID = msg.variableHeader().packetId();
        if (!initialized) {
            LOG.error(() -> "Moquette is not started, internal message cannot be published. CId={}, messageId={}", clientId, messageID);
            throw new IllegalStateException("Can't publish on a server is not yet started");
        }

        LOG.debug(() -> "Publishing message. CId={}, messageId={}", clientId, messageID);

        processor.internalPublish(msg, clientId);
    }

    public void stopServer() {
        LOG.info(() -> "Unbinding server from the configured ports");
        m_acceptor.close();
        LOG.trace(() -> "Stopping MQTT protocol processor");
        processorBootstrapper.shutdown();
        initialized = false;
        scheduler.shutdown();

        LOG.info(() -> "Moquette server has been stopped.");
    }

    /**
     * SPI method used by Broker embedded
     * applications to get list of subscribers.
     * Returns null if the broker is not started.
     *
     * @return list of subscriptions.
     */
    public List<Subscription> getSubscriptions() {
        if (processorBootstrapper == null) {
            return null;
        }
        return processorBootstrapper.getSubscriptions();
    }

    /**
     * SPI method used by Broker embedded
     * applications to add intercept handlers.
     *
     * @param interceptHandler the handler to add.
     */
    public void addInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            LOG.error(() -> "Moquette is not started, MQTT message interceptor cannot be added. InterceptorId={}", interceptHandler.getID());
            throw new IllegalStateException("Can't register interceptors on a server that is not yet started");
        }
        LOG.info(() -> "Adding MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        processor.addInterceptHandler(interceptHandler);
    }

    /**
     * SPI method used by Broker embedded
     * applications to remove intercept handlers.
     *
     * @param interceptHandler the handler to remove.
     */
    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        if (!initialized) {
            LOG.error(() -> "Moquette is not started, MQTT message interceptor cannot be removed. InterceptorId={}", interceptHandler.getID());
            throw new IllegalStateException("Can't deregister interceptors from a server that is not yet started");
        }
        LOG.info(() -> "Removing MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        processor.removeInterceptHandler(interceptHandler);
    }

    /**
     * Returns the connections manager of this
     * broker.
     *
     * @return IConnectionsManager the instance
     * used bt the broker.
     */
    public IConnectionsManager getConnectionsManager() {
        return processorBootstrapper.getConnectionDescriptors();
    }

    public ProtocolProcessor getProcessor() {
        return processor;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
