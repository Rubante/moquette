package mqtt;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ruban.framework.core.utils.commons.DateUtil;

public class ClientCallback implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(ClientCallback.class);

    private AtomicInteger counter = new AtomicInteger(0);

    private AtomicInteger subcounter = new AtomicInteger(0);

    private String clientid;

    private MqttConnectOptions connectionOpt;

    private MqttClient mqttClient;

    private int click = 0;

    private int multiply = 1;

    public void connectionLost(Throwable cause) {
        logger.error("lost", cause);
        boolean reconnect = true;
        while (reconnect) {
            try {
                mqttClient.connect(connectionOpt);
                mqttClient.subscribe("wifi/log");
                mqttClient.subscribe("wifi/log/" + clientid);
                reconnect = false;
            } catch (MqttException exception) {
                logger.error("mqtter", exception);
                try {
                    Thread.sleep(5000 * multiply);
                    click++;
                    multiply = click / 5 + 1;
                } catch (InterruptedException e) {
                    logger.error("error!", e);
                }
            }
        }
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        logger.info("第" + counter.incrementAndGet() + "条消息已发送！");
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String str = new String(message.getPayload(), "utf-8");
        Date sendDate = DateUtil.parseDateTime(str);
        Date date = new Date();
        logger.info("receive:[" + str + "]，时间差：" + DateUtil.diffDateD(date, sendDate) + "秒");
        logger.info("clientId：" + clientid + "，目前条数：" + subcounter.incrementAndGet());

        logger.info("总条数：" + counter.incrementAndGet());
    }

    public AtomicInteger getCounter() {
        return counter;
    }

    public void setCounter(AtomicInteger counter) {
        this.counter = counter;
    }

    public AtomicInteger getSubcounter() {
        return subcounter;
    }

    public void setSubcounter(AtomicInteger subcounter) {
        this.subcounter = subcounter;
    }

    public String getClientid() {
        return clientid;
    }

    public void setClientid(String clientid) {
        this.clientid = clientid;
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public void setMqttClient(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public MqttConnectOptions getConnectionOpt() {
        return connectionOpt;
    }

    public void setConnectionOpt(MqttConnectOptions connectionOpt) {
        this.connectionOpt = connectionOpt;
    }

}