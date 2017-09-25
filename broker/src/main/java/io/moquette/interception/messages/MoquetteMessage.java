package io.moquette.interception.messages;

import io.moquette.spi.impl.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * 扩展消息类型
 * 
 * @author yjwang
 *
 */
public class MoquetteMessage extends MqttMessage {

    private int messageId;

    private String topic;

    private byte[] payload;

    private MqttFixedHeader fixedHeader;

    public MoquetteMessage(MqttFixedHeader mqttFixedHeader, Object variableHeader, Object payload) {
        super(mqttFixedHeader, variableHeader, payload);

        messageId = ((MqttPublishVariableHeader) variableHeader).packetId();
        topic = ((MqttPublishVariableHeader) variableHeader).topicName();

        this.payload = Utils.readBytesAndRewind((ByteBuf) payload);

        fixedHeader = mqttFixedHeader;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public boolean isRetainFlag() {
        return fixedHeader.isRetain();
    }

    public boolean isDupFlag() {
        return fixedHeader.isDup();
    }

    public MqttQoS getQos() {
        return fixedHeader.qosLevel();
    }
}
