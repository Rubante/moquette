package mqtt;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Publisher {

    private static final Logger logger = LoggerFactory.getLogger(Publisher.class);

    private static AtomicInteger counter = new AtomicInteger(0);

    //    private static String ip = "tcp://172.16.32.14:1886";
    private static String ip = "tcp://39.102.113.135:1883";
//    private static String ip = "tcp://localhost:1883";

    public static void main(final String[] args) {

        for (int i = 0; i < 1; i++) {
            Runnable thread = new Runnable() {

                public void run() {

                    int minute = 10;
                    if (args.length >= 2) {
                        minute = Integer.parseInt(args[1]);
                    }

                    connect(RandomStringUtils.randomAlphabetic(14), minute);
                }
            };

            new Thread(thread).start();
        }
    }

    public static void connect(String clientId, final int minute) {

        String broker = ip;

        MemoryPersistence persistence = new MemoryPersistence();

        try {
            final MqttClient sampleClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setUserName("admin");
            connOpts.setPassword("123456".toCharArray());
            connOpts.setKeepAliveInterval(60);

            connOpts.setCleanSession(true);

            PublisherCallback callback = new PublisherCallback();
            callback.setCounter(counter);
            sampleClient.setCallback(callback);
            sampleClient.connect(connOpts);

            sampleClient.subscribe("wifi/lost");

            new Thread(new Runnable() {

                public void run() {
                    for (int i = 0; i < 6 * minute; i++) {
                        // 消息
                        String content = DateUtil.getNowDateTimeStr();
                        MqttMessage message = new MqttMessage(content.getBytes());

                        message.setQos(1);

                        try {
                            sampleClient.publish("wifi/log", message);
                        } catch (MqttException ex) {
                            ex.printStackTrace();
                        }
                        try {
                            Thread.sleep(10000);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                }
            }).start();
        } catch (MqttException me) {
            logger.error("mqtt send error", me);
        }
    }
}
