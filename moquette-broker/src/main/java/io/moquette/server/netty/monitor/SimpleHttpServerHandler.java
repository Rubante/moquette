package io.moquette.server.netty.monitor;

import io.moquette.log.Logger;
import io.moquette.log.LoggerFactory;
import io.moquette.server.netty.metrics.MessageMetricsCollector;
import io.moquette.spi.impl.ProtocolProcessor;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SimpleHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleHttpServerHandler.class);

    private MessageMetricsCollector collector;
    private ProtocolProcessor processor;

    public SimpleHttpServerHandler(MessageMetricsCollector collector, ProtocolProcessor processor) {
        this.processor = processor;
        this.collector = collector;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) {
        try {
            LOG.info(() -> "SimpleHttpServerHandler receive fullHttpRequest=" + fullHttpRequest);

            String result = doHandle(fullHttpRequest);
            LOG.info(() -> "SimpleHttpServerHandler,result=" + result);
            byte[] responseBytes = result.getBytes(StandardCharsets.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(responseBytes));
            response.headers().set("Content-Type", "text/html; charset=utf-8");
            response.headers().setInt("Content-Length", response.content().readableBytes());

            boolean isKeepAlive = HttpUtil.isKeepAlive(response);
            if (!isKeepAlive) {
                ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            } else {
                response.headers().set("Connection", "keep-alive");
                ctx.write(response);
            }
        } catch (Exception e) {
            LOG.error(() -> "channelRead0 exception,", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * 实际处理
     *
     * @param fullHttpRequest HTTP请求参数
     * @return
     */
    private String doHandle(FullHttpRequest fullHttpRequest) {
        if (HttpMethod.GET == fullHttpRequest.method()) {
            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpRequest.uri());
            Map<String, List<String>> params = queryStringDecoder.parameters();
            String result = "read: " + collector.computeMetrics().messagesRead() + ", write: " + collector.computeMetrics().messagesWrote();
            result += "; connections: " + processor.getConnectionDescriptors().getActiveConnectionsNo() + ", " ;
            return result;
        } else if (HttpMethod.POST == fullHttpRequest.method()) {
            return fullHttpRequest.content().toString();
        }

        return "";
    }

}
