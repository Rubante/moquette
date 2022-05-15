package mqtt;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublisherCallback implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(PublisherCallback.class);

    private AtomicInteger counter = new AtomicInteger(0);

    private AtomicInteger lostCounter = new AtomicInteger(0);

    private AtomicInteger subcounter = new AtomicInteger(0);

    private String clientid;

    private MqttConnectOptions connectionOpt;

    private MqttClient mqttClient;

    private int click = 0;

    private int multiply = 1;

    private int qos = 2;

    private int minute = 1;

    public void connectionLost(Throwable cause) {
        logger.error("lost", cause);

        boolean reconnect = true;
        while (reconnect) {
            if (!mqttClient.isConnected()) {
                try {
                    mqttClient.connect(connectionOpt);
                    run();
                    reconnect = false;
                    break;
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
            } else {
                break;
            }

        }
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        logger.info("第" + counter.incrementAndGet() + "条消息已发送！");
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String str = new String(message.getPayload(), "utf-8");
        logger.info("receive:[" + str + "]");
        logger.info("掉线数量：" + lostCounter.incrementAndGet());
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

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public void run() {

        Runnable runnable = () -> {
            try {
                mqttClient.subscribe("wifi/lost");

                int i = 0;
                int index = 10000;
                int client = 1;
                int count = 0;
                while (i < 10 * 60 * minute) {
                    if (index > 11000) {
                        index = 10000;
                        count++;
                    }
                    if (count == 40) {
                        client++;
                        count = 0;
                        if (client > 30) {
                            client = 0;
                        }
                    }

                    Thread.sleep(100);
                    // 消息
                    String content = DateUtil.getNowDateTimeStr();
                    MqttMessage message = new MqttMessage(content.getBytes());

                    message.setQos(qos);
                    mqttClient.publish("wifi/log/" + client + "-" + index, message);
                    index++;
                    i++;
                }

            } catch (MqttException ex) {
                ex.printStackTrace();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

        };

        new Thread(runnable).start();
        new Thread(runnable).start();
        new Thread(runnable).start();
        new Thread(runnable).start();
    }

}