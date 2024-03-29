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

package io.moquette.server.netty;

import io.moquette.BrokerConstants;
import io.moquette.log.Logger;
import io.moquette.log.LoggerFactory;
import io.moquette.server.ServerAcceptor;
import io.moquette.server.config.IConfig;
import io.moquette.server.netty.metrics.*;
import io.moquette.server.netty.monitor.SimpleHttpServerHandler;
import io.moquette.spi.impl.ProtocolProcessor;
import io.moquette.spi.security.ISslContextCreator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.moquette.BrokerConstants.*;

/**
 * netty接受器
 */
public class NettyAcceptor implements ServerAcceptor {

    private static final Logger LOG = LoggerFactory.getLogger(NettyAcceptor.class);

    private static final String MQTT_SUBPROTOCOL_CSV_LIST = "mqtt, mqttv3.1, mqttv3.1.1";

    static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

        @Override
        protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out)
                throws Exception {
            // convert the frame to a ByteBuf
            ByteBuf bb = frame.content();
            // System.out.println("WebSocketFrameToByteBufDecoder decode - " +
            // ByteBufUtil.hexDump(bb));
            bb.retain();
            out.add(bb);
        }
    }

    static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

        @Override
        protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
            // convert the ByteBuf to a WebSocketFrame
            BinaryWebSocketFrame result = new BinaryWebSocketFrame();
            // System.out.println("ByteBufToWebSocketFrameEncoder encode - " +
            // ByteBufUtil.hexDump(bb));
            result.content().writeBytes(bb);
            out.add(result);
        }
    }

    abstract class PipelineInitializer {

        abstract void init(ChannelPipeline pipeline) throws Exception;
    }

    EventLoopGroup m_bossGroup;
    EventLoopGroup m_workerGroup;
    BytesMetricsCollector bytesMetricsCollector = new BytesMetricsCollector();
    MessageMetricsCollector metricsCollector = new MessageMetricsCollector();

    private int nettySoBacklog;
    private boolean nettySoReuseaddr;
    private boolean nettyTcpNodelay;
    private boolean nettySoKeepalive;
    private int nettyChannelTimeoutSeconds;

    private Class<? extends ServerSocketChannel> channelClass;

    @Override
    public void initialize(ProtocolProcessor processor, IConfig props, ISslContextCreator sslCtxCreator)
            throws IOException {
        LOG.info(() -> "Initializing Netty acceptor...");

        nettySoBacklog = Integer.parseInt(props.getProperty(BrokerConstants.NETTY_SO_BACKLOG_PROPERTY_NAME, "128"));
        nettySoReuseaddr = Boolean
                .parseBoolean(props.getProperty(BrokerConstants.NETTY_SO_REUSEADDR_PROPERTY_NAME, "true"));
        nettyTcpNodelay = Boolean
                .parseBoolean(props.getProperty(BrokerConstants.NETTY_TCP_NODELAY_PROPERTY_NAME, "true"));
        nettySoKeepalive = Boolean
                .parseBoolean(props.getProperty(BrokerConstants.NETTY_SO_KEEPALIVE_PROPERTY_NAME, "true"));
        nettyChannelTimeoutSeconds = Integer
                .parseInt(props.getProperty(BrokerConstants.NETTY_CHANNEL_TIMEOUT_SECONDS_PROPERTY_NAME, "10"));

        boolean epoll = Boolean.parseBoolean(props.getProperty(BrokerConstants.NETTY_EPOLL_PROPERTY_NAME, "false"));
        if (epoll) {
            LOG.info(() -> "Netty is using epoll.");
            m_bossGroup = new EpollEventLoopGroup();
            m_workerGroup = new EpollEventLoopGroup();
            channelClass = EpollServerSocketChannel.class;
        } else {
            LOG.info(() -> "Netty is using nio.");
            m_bossGroup = new NioEventLoopGroup();
            m_workerGroup = new NioEventLoopGroup();
            channelClass = NioServerSocketChannel.class;
        }

        NettyMQTTHandler handler = new NettyMQTTHandler(processor);

        initializePlainTCPTransport(handler, props);
        initializeWebSocketTransport(handler, props);
        initializeHttpTransport(processor, props);
        String sslTcpPortProp = props.getProperty(BrokerConstants.SSL_PORT_PROPERTY_NAME);
        String wssPortProp = props.getProperty(BrokerConstants.WSS_PORT_PROPERTY_NAME);
        if (sslTcpPortProp != null || wssPortProp != null) {
            SSLContext sslContext = sslCtxCreator.initSSLContext();
            if (sslContext == null) {
                LOG.error(() -> "Can't initialize SSLHandler layer! Exiting, check your configuration of jks");
                return;
            }
            initializeSSLTCPTransport(handler, props, sslContext);
            initializeWSSTransport(handler, props, sslContext);
        }
    }

    private void initFactory(String host, int port, String protocol, final PipelineInitializer pipeliner) {
        LOG.info(() -> "Initializing server. Protocol={}", protocol);
        ServerBootstrap b = new ServerBootstrap();
        b.group(m_bossGroup, m_workerGroup).channel(channelClass)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        try {
                            pipeliner.init(pipeline);
                        } catch (Throwable th) {
                            LOG.error(() -> "Severe error during pipeline creation", th);
                            throw th;
                        }
                    }
                }).option(ChannelOption.SO_BACKLOG, nettySoBacklog).option(ChannelOption.SO_REUSEADDR, nettySoReuseaddr)
                .childOption(ChannelOption.TCP_NODELAY, nettyTcpNodelay)
                .childOption(ChannelOption.SO_KEEPALIVE, nettySoKeepalive);
        try {
            LOG.info(() -> "Binding server. host={}, port={}", host, port);
            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(host, port);
            LOG.info(() -> "Server has been bound. host={}, port={}", host, port);
            f.sync();
        } catch (InterruptedException ex) {
            LOG.error(() -> "An interruptedException was caught while initializing server. Protocol={}", protocol, ex);
        }
    }

    /**
     * 初始化普通tcp传输
     *
     * @param handler
     * @param props
     * @throws IOException
     */
    private void initializePlainTCPTransport(final NettyMQTTHandler handler, IConfig props) throws IOException {
        LOG.info(() -> "Configuring TCP MQTT transport");
        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
        String tcpPortProp = props.getProperty(PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
        if (DISABLED_PORT_BIND.equals(tcpPortProp)) {
            LOG.info(() -> "Property {} has been set to {}. TCP MQTT will be disabled", BrokerConstants.PORT_PROPERTY_NAME,
                    DISABLED_PORT_BIND);
            return;
        }
        // 空闲超时处理器
        MoquetteIdleTimeoutHandler timeoutHandler = new MoquetteIdleTimeoutHandler();
        initFactory(host, Integer.parseInt(tcpPortProp), "TCP MQTT", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                // pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.ERROR));
                // 用于记录收发字节数
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                // mqtt编解码器
                pipeline.addLast("decoder", new MqttDecoder());
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                // 用于记录收发消息条数
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("messageLogger", new MQTTMessageLogger());
                pipeline.addLast("handler", handler);
            }
        });
    }

    /**
     * 初始化webSocket
     *
     * @param handler
     * @param props
     * @throws IOException
     */
    private void initializeWebSocketTransport(final NettyMQTTHandler handler, IConfig props) throws IOException {
        LOG.info(() -> "Configuring Websocket MQTT transport");
        String webSocketPortProp = props.getProperty(WEB_SOCKET_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
        if (DISABLED_PORT_BIND.equals(webSocketPortProp)) {
            // Do nothing no WebSocket configured
            LOG.info(() -> "Property {} has been setted to {}. Websocket MQTT will be disabled",
                    BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
            return;
        }
        int port = Integer.parseInt(webSocketPortProp);

        final MoquetteIdleTimeoutHandler timeoutHandler = new MoquetteIdleTimeoutHandler();

        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
        initFactory(host, port, "Websocket MQTT", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast("webSocketHandler",
                        new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST));
                pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MqttDecoder());
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("messageLogger", new MQTTMessageLogger());
                pipeline.addLast("handler", handler);
            }
        });
    }

    private void initializeSSLTCPTransport(final NettyMQTTHandler handler, IConfig props, final SSLContext sslContext)
            throws IOException {
        LOG.info(() -> "Configuring SSL MQTT transport");
        String sslPortProp = props.getProperty(SSL_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
        if (DISABLED_PORT_BIND.equals(sslPortProp)) {
            // Do nothing no SSL configured
            LOG.info(() -> "Property {} has been set to {}. SSL MQTT will be disabled",
                    BrokerConstants.SSL_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
            return;
        }

        int sslPort = Integer.parseInt(sslPortProp);
        LOG.info(() -> "Starting SSL on port {}", sslPort);

        final MoquetteIdleTimeoutHandler timeoutHandler = new MoquetteIdleTimeoutHandler();
        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
        String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
        final boolean needsClientAuth = Boolean.parseBoolean(sNeedsClientAuth);
        initFactory(host, sslPort, "SSL MQTT", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) throws Exception {
                pipeline.addLast("ssl", createSslHandler(sslContext, needsClientAuth));
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                // pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.ERROR));
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MqttDecoder());
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("messageLogger", new MQTTMessageLogger());
                pipeline.addLast("handler", handler);
            }
        });
    }

    private void initializeWSSTransport(final NettyMQTTHandler handler, IConfig props, final SSLContext sslContext)
            throws IOException {
        LOG.info(() -> "Configuring secure websocket MQTT transport");
        String sslPortProp = props.getProperty(WSS_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
        if (DISABLED_PORT_BIND.equals(sslPortProp)) {
            // Do nothing no SSL configured
            LOG.info(() -> "Property {} has been set to {}. Secure websocket MQTT will be disabled",
                    BrokerConstants.WSS_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
            return;
        }
        int sslPort = Integer.parseInt(sslPortProp);
        final MoquetteIdleTimeoutHandler timeoutHandler = new MoquetteIdleTimeoutHandler();
        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
        String sNeedsClientAuth = props.getProperty(BrokerConstants.NEED_CLIENT_AUTH, "false");
        final boolean needsClientAuth = Boolean.parseBoolean(sNeedsClientAuth);
        initFactory(host, sslPort, "Secure websocket", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) throws Exception {
                pipeline.addLast("ssl", createSslHandler(sslContext, needsClientAuth));
                pipeline.addLast("httpEncoder", new HttpResponseEncoder());
                pipeline.addLast("httpDecoder", new HttpRequestDecoder());
                pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
                pipeline.addLast("webSocketHandler",
                        new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST));
                pipeline.addLast("ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
                pipeline.addLast("bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
                pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
                pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
                pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
                pipeline.addLast("decoder", new MqttDecoder());
                pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
                pipeline.addLast("messageLogger", new MQTTMessageLogger());
                pipeline.addLast("handler", handler);
            }
        });
    }

    /**
     * 初始化HTTP服务，用于显示统计信息
     *
     * @param props
     * @throws IOException
     */
    private void initializeHttpTransport(ProtocolProcessor processor, IConfig props) throws IOException {
        LOG.info(() -> "Configuring HTTP transport");
        String httpPortProp = props.getProperty(HTTP_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
        if (DISABLED_PORT_BIND.equals(httpPortProp)) {
            // Do nothing no WebSocket configured
            LOG.info(() -> "Property {} has been setted to {}. HTTP will be disabled",
                    BrokerConstants.HTTP_PORT_PROPERTY_NAME, DISABLED_PORT_BIND);
            return;
        }
        int port = Integer.parseInt(httpPortProp);

        String host = props.getProperty(BrokerConstants.HOST_PROPERTY_NAME);
        initFactory(host, port, "HTTP Server", new PipelineInitializer() {

            @Override
            void init(ChannelPipeline pipeline) {
                pipeline.addLast(new HttpRequestDecoder());
                pipeline.addLast(new HttpResponseEncoder());
                pipeline.addLast(new HttpObjectAggregator(512 * 1024));
                pipeline.addLast(new SimpleHttpServerHandler(metricsCollector, processor));
            }
        });
    }

    public void close() {
        LOG.info(() -> "Closing Netty acceptor...");
        if (m_workerGroup == null || m_bossGroup == null) {
            LOG.error(() -> "Netty acceptor is not initialized");
            throw new IllegalStateException("Invoked close on an Acceptor that wasn't initialized");
        }
        Future<?> workerWaiter = m_workerGroup.shutdownGracefully();
        Future<?> bossWaiter = m_bossGroup.shutdownGracefully();

        /*
         * We shouldn't raise an IllegalStateException if we are interrupted. If we did so, the
         * broker is not shut down properly.
         */
        LOG.info(() -> "Waiting for worker and boss event loop groups to terminate...");
        try {
            workerWaiter.await(10, TimeUnit.SECONDS);
            bossWaiter.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException iex) {
            LOG.warn(() -> "An InterruptedException was caught while waiting for event loops to terminate...");
        }

        if (!m_workerGroup.isTerminated()) {
            LOG.warn(() -> "Forcing shutdown of worker event loop...");
            m_workerGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
        }

        if (!m_bossGroup.isTerminated()) {
            LOG.warn(() -> "Forcing shutdown of boss event loop...");
            m_bossGroup.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS);
        }

        LOG.info(() -> "Collecting message metrics...");
        MessageMetrics metrics = metricsCollector.computeMetrics();
        LOG.info(() -> "Metrics have been collected. Read messages={}, written messages={}", metrics.messagesRead(),
                metrics.messagesWrote());

        LOG.info(() -> "Collecting bytes metrics...");
        BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
        LOG.info(() -> "Bytes metrics have been collected. Read bytes={}, written bytes={}", bytesMetrics.readBytes(),
                bytesMetrics.wroteBytes());
    }

    private ChannelHandler createSslHandler(SSLContext sslContext, boolean needsClientAuth) {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        if (needsClientAuth) {
            sslEngine.setNeedClientAuth(true);
        }
        return new SslHandler(sslEngine);
    }
}
